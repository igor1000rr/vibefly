// Package rootx — детект root и helper для выполнения команд через su.
//
// На Android root это обычно Magisk — бинарь /system/bin/su или /system/xbin/su,
// который при первом вызове спрашивает разрешение через Magisk Manager, потом
// выдаёт root-оболочку.
//
// Используем из metrics (читать заблокированные SELinux пути) и supervisor/cgroup
// (писать в /sys/fs/cgroup — без root это EACCES).
package rootx

import (
	"context"
	"errors"
	"os"
	"os/exec"
	"strings"
	"sync"
	"time"
)

var (
	once     sync.Once
	detected bool
	suPath   string
)

// Available — доступен ли root в данном окружении.
func Available() bool {
	once.Do(detect)
	return detected
}

// Path — абсолютный путь к su бинарю, или "" если недоступен.
func Path() string {
	once.Do(detect)
	return suPath
}

func detect() {
	candidates := []string{
		"/system/bin/su",
		"/system/xbin/su",
		"/sbin/su",
		"/su/bin/su",
		"/magisk/.core/bin/su",
	}
	for _, p := range candidates {
		if info, err := os.Stat(p); err == nil && !info.IsDir() {
			suPath = p
			break
		}
	}
	if suPath == "" {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, suPath, "-c", "id").Output()
	if err != nil {
		suPath = ""
		return
	}
	detected = strings.Contains(string(out), "uid=0")
}

// Run — выполнить шелл-команду от root'а. Возвращает combined output.
func Run(ctx context.Context, shellCmd string) ([]byte, error) {
	if !Available() {
		return nil, errors.New("rootx: root недоступен")
	}
	return exec.CommandContext(ctx, suPath, "-c", shellCmd).CombinedOutput()
}

// ReadFile — читает файл через root (для файлов заблокированных SELinux для аппа).
// Сначала пробуем обычный os.ReadFile — если получилось, root не нужен.
func ReadFile(ctx context.Context, path string) ([]byte, error) {
	if data, err := os.ReadFile(path); err == nil {
		return data, nil
	}
	if !Available() {
		return nil, errors.New("rootx: нет доступа и root недоступен")
	}
	return Run(ctx, "cat "+shellEscape(path))
}

// WriteFile — пишет данные в файл через root (для /sys/fs/cgroup/*).
func WriteFile(ctx context.Context, path, content string) error {
	if !Available() {
		return errors.New("rootx: root недоступен")
	}
	cmd := "echo " + shellEscape(content) + " > " + shellEscape(path)
	_, err := Run(ctx, cmd)
	return err
}

func shellEscape(s string) string {
	return "'" + strings.ReplaceAll(s, "'", "'\"'\"'") + "'"
}
