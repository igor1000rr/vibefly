// Package rootfs — распаковка и верификация Alpine minirootfs для chroot-runtime.
//
// Архитектура (фаза 2):
//
//	baseDir/— shared read-only Alpine, один на все приложения
//	├─ bin/busybox       — базовые утилиты
//	├─ etc/apk/repositories — источники пакетов
//	└─ ...
//
// EnsureBase() вызывается при старте агента. Идемпотентно:
// если baseDir/.vibefly-rootfs-ready существует — пропускается.
// При обновлении APK можно вручную удалить этот файл — распаковка повторится.
package rootfs

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"time"

	"by.vibefly/agent/internal/rootx"
)

// sentinelName — файл-маркер в baseDir чтобы понять что распаковка уже была.
const sentinelName = ".vibefly-rootfs-ready"

// extractTimeout — распаковка ~3 MB Alpine занимает 5-10 сек, даём запас.
const extractTimeout = 60 * time.Second

// Manager — жизненный цикл Alpine base rootfs.
type Manager struct {
	logger      *slog.Logger
	baseDir     string // куда распаковываем, в prod = filesDir/rootfs-base
	tarballPath string // исходник, в prod = filesDir/rootfs/alpine-minirootfs.tar.gz
}

// Options — параметры NewManager.
type Options struct {
	Logger      *slog.Logger
	BaseDir     string
	TarballPath string
}

func NewManager(opts Options) *Manager {
	if opts.Logger == nil {
		opts.Logger = slog.Default()
	}
	return &Manager{
		logger:      opts.Logger.With("component", "rootfs"),
		baseDir:     opts.BaseDir,
		tarballPath: opts.TarballPath,
	}
}

// BaseDir — возвращает путь к base rootfs (пустая строка если не готов).
func (m *Manager) BaseDir() string {
	if m.IsReady() {
		return m.baseDir
	}
	return ""
}

// IsReady — распакован ли rootfs.
func (m *Manager) IsReady() bool {
	if m.baseDir == "" {
		return false
	}
	info, err := os.Stat(filepath.Join(m.baseDir, sentinelName))
	return err == nil && !info.IsDir()
}

// EnsureBase — идемпотентно распаковывает tarball в baseDir.
//
// Шаги:
//  1. Если IsReady() — выходим.
//  2. Проверяем что tarball существует и не пуст.
//  3. mkdir -p baseDir
//  4. tar xzf tarball -C baseDir -p --numeric-owner
//  5. Пишем sentinel-файл.
//
// Распаковка через host tar без root если этого хватает. На Android некоторые файлы
// в Alpine имеют setuid флаги (su, ping) — host shell их не сохранит без root.
// Но для Node.js/Python setuid не нужен, поэтому пробуем host tar first.
func (m *Manager) EnsureBase(ctx context.Context) error {
	if m.IsReady() {
		m.logger.Info("rootfs уже готов", "path", m.baseDir)
		return nil
	}
	if m.tarballPath == "" || m.baseDir == "" {
		return errors.New("rootfs: не заданы tarball_path или base_dir")
	}
	info, err := os.Stat(m.tarballPath)
	if err != nil {
		return fmt.Errorf("rootfs: tarball %s: %w", m.tarballPath, err)
	}
	if info.Size() < 1024 {
		return fmt.Errorf("rootfs: tarball подозрительно мал (%d байт)", info.Size())
	}

	// Очистка полу-состояния (если предыдущий extract упал).
	if _, err := os.Stat(m.baseDir); err == nil {
		m.logger.Info("сносим недораспакованный baseDir", "path", m.baseDir)
		if err := removeAllBestEffort(ctx, m.baseDir); err != nil {
			m.logger.Warn("не удалось снести старый baseDir", "err", err)
		}
	}
	if err := os.MkdirAll(m.baseDir, 0o755); err != nil {
		return fmt.Errorf("rootfs: mkdir baseDir: %w", err)
	}

	extractCtx, cancel := context.WithTimeout(ctx, extractTimeout)
	defer cancel()

	m.logger.Info("распаковка Alpine rootfs", "src", m.tarballPath, "dst", m.baseDir, "size_bytes", info.Size())
	start := time.Now()
	if err := extractTar(extractCtx, m.tarballPath, m.baseDir); err != nil {
		return fmt.Errorf("rootfs: extract: %w", err)
	}
	m.logger.Info("rootfs распакован", "dur_ms", time.Since(start).Milliseconds())

	// Санити-чек: busybox должен быть на месте.
	bb := filepath.Join(m.baseDir, "bin", "busybox")
	if _, err := os.Stat(bb); err != nil {
		return fmt.Errorf("rootfs: busybox не найден после extract: %w", err)
	}

	if err := writeSentinel(m.baseDir); err != nil {
		return fmt.Errorf("rootfs: sentinel: %w", err)
	}
	m.logger.Info("rootfs ready", "path", m.baseDir)
	return nil
}

// extractTar — выполняет `tar xzf <src> -C <dst> -p --numeric-owner`.
//
// Сначала пробуем обычный tar в PATH (host shell). Если нет прав (EACCES для
// некоторых setuid файлов) или tar не найден — fallback на rootx.Run.
func extractTar(ctx context.Context, src, dst string) error {
	// host tar — ожидаем что в Android shell есть busybox-tar (в toybox тоже есть).
	cmd := exec.CommandContext(ctx, "tar", "xzf", src, "-C", dst, "--numeric-owner")
	out, err := cmd.CombinedOutput()
	if err == nil {
		return nil
	}
	// Fallback через root — setuid файлы + setcap сохранятся.
	if !rootx.Available() {
		return fmt.Errorf("host tar failed (%v) и root недоступен, вывод: %s", err, string(out))
	}
	cmdStr := fmt.Sprintf("tar xzf %q -C %q -p --numeric-owner", src, dst)
	rootOut, rootErr := rootx.Run(ctx, cmdStr)
	if rootErr != nil {
		return fmt.Errorf("tar via root failed: %v, вывод: %s", rootErr, string(rootOut))
	}
	return nil
}

// removeAllBestEffort — rm -rf, сначала os.RemoveAll, потом rootx если не вышло.
func removeAllBestEffort(ctx context.Context, path string) error {
	if err := os.RemoveAll(path); err == nil {
		return nil
	}
	if !rootx.Available() {
		return os.RemoveAll(path) // вернём первоначальную ошибку
	}
	cmd := fmt.Sprintf("rm -rf %q", path)
	_, err := rootx.Run(ctx, cmd)
	return err
}

// writeSentinel — создаёт файл-маркер что rootfs готов.
func writeSentinel(baseDir string) error {
	return os.WriteFile(filepath.Join(baseDir, sentinelName),
		[]byte(time.Now().UTC().Format(time.RFC3339)+"\n"), 0o644)
}
