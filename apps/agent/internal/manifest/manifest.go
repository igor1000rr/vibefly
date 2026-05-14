// Package manifest — парсер vibefly.toml из пользовательского GitHub-репо.
//
// Кейс: юзер в +Deploy вводит https://github.com/owner/repo — агент скачивает
// raw.githubusercontent.com/owner/repo/<branch>/vibefly.toml, парсит и использует
// как источник правды для binary_url/start_cmd/port и пр.
//
// Пример vibefly.toml:
//
//	name = "My App"
//	binary_url = "https://github.com/me/myapp/releases/latest/download/myapp-arm64.tar.gz"
//	start_cmd = "./myapp serve"
//	port = 8080
//	memory_max = "256M"
//	cpu_quota = "50%"
//	autostart = true
//	restart_policy = "on-failure"
//	[env]
//	DATABASE_URL = "sqlite:///data.db"
//	LOG_LEVEL = "info"
package manifest

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/pelletier/go-toml/v2"
)

// Manifest — распарсенный vibefly.toml.
type Manifest struct {
	Name          string            `toml:"name"`
	BinaryURL     string            `toml:"binary_url"`
	StartCmd      string            `toml:"start_cmd"`
	Port          int               `toml:"port"`
	MemoryMax     string            `toml:"memory_max"`
	CPUQuota      string            `toml:"cpu_quota"`
	Autostart     bool              `toml:"autostart"`
	RestartPolicy string            `toml:"restart_policy"`
	Env           map[string]string `toml:"env"`
}

// ParseGitHubURL — из https://github.com/owner/repo или git@github.com:owner/repo.git
// извлекает (owner, repo). Иначе error.
func ParseGitHubURL(raw string) (owner, repo string, err error) {
	raw = strings.TrimSpace(raw)
	raw = strings.TrimSuffix(raw, ".git")
	raw = strings.TrimSuffix(raw, "/")

	// SSH формат: git@github.com:owner/repo
	if strings.HasPrefix(raw, "git@github.com:") {
		rest := strings.TrimPrefix(raw, "git@github.com:")
		parts := strings.SplitN(rest, "/", 2)
		if len(parts) != 2 || parts[0] == "" || parts[1] == "" {
			return "", "", errors.New("manifest: невалидный SSH-URL")
		}
		return parts[0], parts[1], nil
	}

	// HTTPS: https://github.com/owner/repo
	u, err := url.Parse(raw)
	if err != nil {
		return "", "", fmt.Errorf("manifest: invalid URL: %w", err)
	}
	if u.Host != "github.com" && u.Host != "www.github.com" {
		return "", "", fmt.Errorf("manifest: ожидается github.com, получил %q", u.Host)
	}
	parts := strings.Split(strings.Trim(u.Path, "/"), "/")
	if len(parts) < 2 || parts[0] == "" || parts[1] == "" {
		return "", "", errors.New("manifest: ожидается /owner/repo")
	}
	return parts[0], parts[1], nil
}

// FetchFromGitHub — скачивает vibefly.toml из raw.githubusercontent.com.
// Пробует по очереди branches (обычно ["main", "master"]).
// Возвращает первый успешный результат.
func FetchFromGitHub(ctx context.Context, owner, repo string, branches []string) (*Manifest, string, error) {
	if len(branches) == 0 {
		branches = []string{"main", "master"}
	}
	client := &http.Client{Timeout: 15 * time.Second}
	var lastErr error
	for _, branch := range branches {
		u := fmt.Sprintf("https://raw.githubusercontent.com/%s/%s/%s/vibefly.toml", owner, repo, branch)
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
		if err != nil {
			return nil, "", err
		}
		req.Header.Set("User-Agent", "vibefly-agent/0.1.1")
		resp, err := client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		if resp.StatusCode == http.StatusNotFound {
			_ = resp.Body.Close()
			lastErr = fmt.Errorf("ветка %q: 404", branch)
			continue
		}
		if resp.StatusCode != http.StatusOK {
			_ = resp.Body.Close()
			lastErr = fmt.Errorf("ветка %q: статус %d", branch, resp.StatusCode)
			continue
		}
		data, err := io.ReadAll(io.LimitReader(resp.Body, 64*1024))
		_ = resp.Body.Close()
		if err != nil {
			return nil, "", fmt.Errorf("read manifest: %w", err)
		}
		var m Manifest
		if err := toml.Unmarshal(data, &m); err != nil {
			return nil, "", fmt.Errorf("parse vibefly.toml: %w", err)
		}
		if m.BinaryURL == "" && m.StartCmd == "" {
			return nil, "", errors.New("пустой манифест: ни binary_url, ни start_cmd не заданы")
		}
		return &m, branch, nil
	}
	if lastErr == nil {
		lastErr = errors.New("не найден vibefly.toml")
	}
	return nil, "", lastErr
}

// FetchFromRepoURL — комбинирует ParseGitHubURL + FetchFromGitHub.
// branch="" → попробует main потом master.
func FetchFromRepoURL(ctx context.Context, repoURL, branch string) (*Manifest, string, error) {
	owner, repo, err := ParseGitHubURL(repoURL)
	if err != nil {
		return nil, "", err
	}
	branches := []string{"main", "master"}
	if branch != "" {
		branches = []string{branch}
	}
	return FetchFromGitHub(ctx, owner, repo, branches)
}
