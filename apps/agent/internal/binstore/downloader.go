// Package binstore — скачивание бинарей и ресурсов приложений в workdir.
//
// Поддерживаем:
//   • Одиночный бинарь (DownloadBinary) → chmod 0755, лежит в workdir/binary
//   • .tar.gz/.tgz и .zip (DownloadAndExtract) → распаковка в workdir
//
// Ограничения:
//   • только HTTPS URL (безопасность)
//   • timeout 90s на весь download
//   • max 200 MB на файл
package binstore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// MaxBinarySize — верхняя граница размера скачиваемого файла.
const MaxBinarySize int64 = 200 * 1024 * 1024 // 200 MB

// DefaultTimeout — таймаут на весь download.
const DefaultTimeout = 90 * time.Second

// Downloader — скачивает файлы по URL в файловую систему.
type Downloader struct {
	http *http.Client
}

func New() *Downloader {
	return &Downloader{
		http: &http.Client{
			Timeout: DefaultTimeout,
		},
	}
}

// DownloadBinary — скачивает binaryURL в workdir/binary, выставляет chmod 0755.
//
// Имя файла всегда "binary" — просто и предсказуемо в start_cmd (используй "./binary").
func (d *Downloader) DownloadBinary(ctx context.Context, binaryURL, workdir string) (string, error) {
	return d.downloadToFile(ctx, binaryURL, filepath.Join(workdir, "binary"), 0o755)
}

// DownloadAndExtract — скачивает архив archiveURL, распаковывает в workdir,
// исходный архив удаляет. Возвращает путь к первому executable-файлу
// внутри (например myapp-arm64-v1.2/myapp) или "".
//
// Для start_cmd это полезно: мы не знаем вперёд какая структура в архиве, но если
// там один бинарь — возвращённый путь можно подставить в spec.StartCmd.
func (d *Downloader) DownloadAndExtract(ctx context.Context, archiveURL, workdir string) (executable string, err error) {
	kind := DetectArchive(archiveURL)
	if kind == "" {
		return "", fmt.Errorf("binstore: не выходит распознать формат (ожидается .tar.gz/.tgz/.zip): %s", archiveURL)
	}
	archivePath := filepath.Join(workdir, ".vibefly-download."+kind)
	if _, err := d.downloadToFile(ctx, archiveURL, archivePath, 0o644); err != nil {
		return "", err
	}
	return ExtractArchive(archivePath, workdir, kind)
}

// downloadToFile — общий helper. Скачивает url в targetPath с mode правами.
func (d *Downloader) downloadToFile(ctx context.Context, rawURL, targetPath string, mode os.FileMode) (string, error) {
	if err := validateURL(rawURL); err != nil {
		return "", err
	}
	if err := os.MkdirAll(filepath.Dir(targetPath), 0o755); err != nil {
		return "", fmt.Errorf("binstore: mkdir: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return "", fmt.Errorf("binstore: new request: %w", err)
	}
	req.Header.Set("User-Agent", "vibefly-agent/0.1.1 (phone-as-a-server)")

	resp, err := d.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("binstore: http: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", fmt.Errorf("binstore: bad status %d от %s", resp.StatusCode, rawURL)
	}
	if resp.ContentLength > MaxBinarySize {
		return "", fmt.Errorf("binstore: файл слишком большой (%d > %d)", resp.ContentLength, MaxBinarySize)
	}

	file, err := os.OpenFile(targetPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, mode)
	if err != nil {
		return "", fmt.Errorf("binstore: create file: %w", err)
	}
	defer file.Close()

	limited := io.LimitReader(resp.Body, MaxBinarySize+1)
	written, err := io.Copy(file, limited)
	if err != nil {
		_ = os.Remove(targetPath)
		return "", fmt.Errorf("binstore: copy: %w", err)
	}
	if written > MaxBinarySize {
		_ = os.Remove(targetPath)
		return "", fmt.Errorf("binstore: файл превысил лимит %d в процессе скачивания", MaxBinarySize)
	}
	if written == 0 {
		_ = os.Remove(targetPath)
		return "", errors.New("binstore: скачан 0 байт")
	}
	if err := os.Chmod(targetPath, mode); err != nil {
		return "", fmt.Errorf("binstore: chmod: %w", err)
	}
	return targetPath, nil
}

// validateURL — разрешаем только https://… (и http:// только для localhost — для тестов).
func validateURL(raw string) error {
	u, err := url.Parse(raw)
	if err != nil {
		return fmt.Errorf("binstore: invalid URL: %w", err)
	}
	switch u.Scheme {
	case "https":
		return nil
	case "http":
		if strings.HasPrefix(u.Host, "127.0.0.1") || strings.HasPrefix(u.Host, "localhost") {
			return nil
		}
		return errors.New("binstore: http:// разрешён только для localhost")
	default:
		return fmt.Errorf("binstore: недопустимая схема %q (разрешены https или http для localhost)", u.Scheme)
	}
}
