// Package binstore — скачивание бинарей и ресурсов приложений в workdir.
//
// Используется при POST /apps {binary_url: "https://..."}: агент скачивает
// бинарь, кладёт в workdir/<id>/binary, делает chmod 755. После этого start_cmd
// может быть просто "./binary --port 8080".
//
// Ограничения:
//   • только HTTPS URL (безопасность)
//   • timeout 90s на весь download
//   • max 200 MB (хватит на cloudflared, cap-typical бинари)
//   • никаких промежуточных редиректов на http:// — GitHub releases возвращает 302 на
//     CDN, но тоже https.
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
// Возвращает абсолютный путь до скачанного файла или ошибку.
// Имя файла всегда "binary" — просто и предсказуемо в start_cmd (используй "./binary").
func (d *Downloader) DownloadBinary(ctx context.Context, binaryURL, workdir string) (string, error) {
	if err := validateURL(binaryURL); err != nil {
		return "", err
	}
	if err := os.MkdirAll(workdir, 0o755); err != nil {
		return "", fmt.Errorf("binstore: mkdir workdir: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, binaryURL, nil)
	if err != nil {
		return "", fmt.Errorf("binstore: new request: %w", err)
	}
	req.Header.Set("User-Agent", "vibefly-agent/0.0.6 (phone-as-a-server)")

	resp, err := d.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("binstore: http: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", fmt.Errorf("binstore: bad status %d от %s", resp.StatusCode, binaryURL)
	}

	// Check Content-Length если сервер вернул — отбораем слишком большие файлы раньше.
	if resp.ContentLength > MaxBinarySize {
		return "", fmt.Errorf("binstore: файл слишком большой (%d > %d)", resp.ContentLength, MaxBinarySize)
	}

	targetPath := filepath.Join(workdir, "binary")
	file, err := os.OpenFile(targetPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o755)
	if err != nil {
		return "", fmt.Errorf("binstore: create file: %w", err)
	}
	defer file.Close()

	// LimitedReader — вторая линия защиты: если ContentLength врёт или пустой,
	// обрываем на MaxBinarySize+1 (используем +1 чтобы обнаружить переполнение).
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

	// chmod ещё раз на всякий случай (umask может зарезать при создании).
	if err := os.Chmod(targetPath, 0o755); err != nil {
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
