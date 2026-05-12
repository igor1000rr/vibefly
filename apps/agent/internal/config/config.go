// Package config — загрузка конфигурации агента из TOML.
package config

import (
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/pelletier/go-toml/v2"
)

// TunnelConfig — параметры публичного ingress через Cloudflare Tunnel.
type TunnelConfig struct {
	// Enabled — если true, агент инстанцирует Cloudflared manager.
	// Если false — менеджер не создаётся, эндпоинты /tunnel вернут 503.
	Enabled bool `toml:"enabled"`

	// Autostart — пытаться поднять туннель сразу при старте агента.
	// Если false — только при ручном POST /tunnel/start.
	Autostart bool `toml:"autostart"`

	// Binary — путь к cloudflared. Пустое = искать в $PATH.
	Binary string `toml:"binary"`

	// Target — куда проксировать. По умолчанию агентский listen-адрес.
	Target string `toml:"target"`

	// StartupTimeout — сколько ждать публичный URL в stderr.
	StartupTimeout time.Duration `toml:"startup_timeout"`
}

// Config — финальная конфигурация агента.
type Config struct {
	Listen    string       `toml:"listen"`
	AuthToken string       `toml:"auth_token"`
	AppsDir   string       `toml:"apps_dir"`
	LogsDir   string       `toml:"logs_dir"`
	Tunnel    TunnelConfig `toml:"tunnel"`
}

// Default возвращает дефолтную конфигурацию.
func Default() Config {
	return Config{
		Listen:    "127.0.0.1:3001",
		AuthToken: "",
		AppsDir:   "/var/lib/vibefly/apps",
		LogsDir:   "/var/log/vibefly",
		Tunnel: TunnelConfig{
			Enabled:        false,
			Autostart:      false,
			Binary:         "cloudflared",
			Target:         "", // авто-подставится из Listen
			StartupTimeout: 60 * time.Second,
		},
	}
}

// Load читает TOML-конфиг с диска и сливает с дефолтами.
func Load(path string) (Config, error) {
	cfg := Default()

	data, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return cfg, fmt.Errorf("файл конфига отсутствует: %w", err)
		}
		return cfg, fmt.Errorf("не удалось прочитать конфиг: %w", err)
	}

	if err := toml.Unmarshal(data, &cfg); err != nil {
		return cfg, fmt.Errorf("не удалось распарсить TOML: %w", err)
	}

	// Если target не задан явно — берём http://<listen>.
	if cfg.Tunnel.Target == "" {
		cfg.Tunnel.Target = "http://" + cfg.Listen
	}
	return cfg, nil
}
