package binstore

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// DetectArchive — по расширению URL'а пытаемся понять что за формат.
//
// Поддерживаемые расширения:
//   - .tar.gz / .tgz
//   - .zip
// Бинарь без расширения (например myapp-arm64) — возвращает "".
func DetectArchive(urlOrPath string) string {
	lower := strings.ToLower(urlOrPath)
	// Сначала .tar.gz/.tgz — они имеют приоритет перед общим .gz.
	switch {
	case strings.HasSuffix(lower, ".tar.gz"), strings.HasSuffix(lower, ".tgz"):
		return "tar.gz"
	case strings.HasSuffix(lower, ".zip"):
		return "zip"
	}
	return ""
}

// ExtractArchive — распаковывает архив archivePath в destDir.
// После успеха удаляет исходный архив (экономия места на телефоне).
//
// Возвращает путь к первому найденному executable-файлу в destDir, или "" если не
// нашёл. Решение "executable" — по правам файла в архиве (mode & 0o111).
//
// Безопасность:
//   - Отказываемся от путей с ".." (path traversal)
//   - Отказываемся от абсолютных путей внутри архива
//   - Символические ссылки игнорируются
func ExtractArchive(archivePath, destDir string, kind string) (executable string, err error) {
	if err := os.MkdirAll(destDir, 0o755); err != nil {
		return "", fmt.Errorf("mkdir dest: %w", err)
	}
	switch kind {
	case "tar.gz":
		executable, err = extractTarGz(archivePath, destDir)
	case "zip":
		executable, err = extractZip(archivePath, destDir)
	default:
		return "", fmt.Errorf("unsupported archive kind %q", kind)
	}
	if err != nil {
		return "", err
	}
	// Чистим архив после успешной распаковки.
	_ = os.Remove(archivePath)
	return executable, nil
}

func extractTarGz(archivePath, destDir string) (string, error) {
	f, err := os.Open(archivePath)
	if err != nil {
		return "", err
	}
	defer f.Close()

	gz, err := gzip.NewReader(f)
	if err != nil {
		return "", fmt.Errorf("gzip: %w", err)
	}
	defer gz.Close()

	tr := tar.NewReader(gz)
	var firstExec string
	for {
		hdr, err := tr.Next()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return "", fmt.Errorf("tar next: %w", err)
		}
		target, ok := safeJoin(destDir, hdr.Name)
		if !ok {
			continue // path traversal — пропускаем
		}
		switch hdr.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, 0o755); err != nil {
				return "", err
			}
		case tar.TypeReg, tar.TypeRegA:
			if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
				return "", err
			}
			mode := os.FileMode(hdr.Mode) & 0o777
			if mode == 0 {
				mode = 0o644
			}
			out, err := os.OpenFile(target, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, mode)
			if err != nil {
				return "", err
			}
			// LimitReader на 200MB на один файл — защита от zip-bomb.
			_, copyErr := io.Copy(out, io.LimitReader(tr, MaxBinarySize))
			_ = out.Close()
			if copyErr != nil {
				return "", copyErr
			}
			if firstExec == "" && mode&0o111 != 0 {
				firstExec = target
			}
		}
		// symlinks, devices, fifo — игнорируем.
	}
	return firstExec, nil
}

func extractZip(archivePath, destDir string) (string, error) {
	r, err := zip.OpenReader(archivePath)
	if err != nil {
		return "", fmt.Errorf("zip open: %w", err)
	}
	defer r.Close()

	var firstExec string
	for _, zf := range r.File {
		target, ok := safeJoin(destDir, zf.Name)
		if !ok {
			continue
		}
		if zf.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0o755); err != nil {
				return "", err
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return "", err
		}
		mode := zf.Mode() & 0o777
		if mode == 0 {
			mode = 0o644
		}
		src, err := zf.Open()
		if err != nil {
			return "", err
		}
		out, err := os.OpenFile(target, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, mode)
		if err != nil {
			_ = src.Close()
			return "", err
		}
		_, copyErr := io.Copy(out, io.LimitReader(src, MaxBinarySize))
		_ = src.Close()
		_ = out.Close()
		if copyErr != nil {
			return "", copyErr
		}
		if firstExec == "" && mode&0o111 != 0 {
			firstExec = target
		}
	}
	return firstExec, nil
}

// safeJoin — защищает от path traversal. Разрешаем только пути внутри baseDir.
func safeJoin(baseDir, name string) (string, bool) {
	cleaned := filepath.Clean("/" + name) // левая "/" отбивает любые ".."
	abs := filepath.Join(baseDir, cleaned)
	if !strings.HasPrefix(abs, filepath.Clean(baseDir)+string(os.PathSeparator)) && abs != filepath.Clean(baseDir) {
		return "", false
	}
	return abs, true
}
