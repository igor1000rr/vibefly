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
	Enabled        bool          `toml:"enabled"`
	Autostart      bool          `toml:"autostart"`
	Binary         string        `toml:"binary"`
	Target         string        `toml:"target"`
	StartupTimeout time.Duration `toml:"startup_timeout"`
}

// Config — финальная конфигурация агента.
type Config struct {
	Listen    string `toml:"listen"`
	AuthToken string `toml:"auth_token"`
	AppsDir   string `toml:"apps_dir"`
	LogsDir   string `toml:"logs_dir"`

	// SeedDemoApps — заполнять ли Store фейк-приложениями (amina-bot и др.)
	// когда supervisor недоступен (например на Windows-девхосте).
	SeedDemoApps bool `toml:"seed_demo_apps"`

	Tunnel TunnelConfig `toml:"tunnel"`

	// Фаза 2: инфраструктура chroot-runtime.
	// Если RootfsTarballPath пуст — chroot не инициализируется (режим фазы 1).
	// Оба поля выставляет RuntimeManager на Android в buildConfig().
	RootfsBaseDir     string `toml:"rootfs_base_dir"`
	RootfsTarballPath string `toml:"rootfs_tarball_path"`
}

// Default возвращает дефолтную конфигурацию.
func Default() Config {
	return Config{
		Listen:       "127.0.0.1:3001",
		AuthToken:    "",
		AppsDir:      "/var/lib/vibefly/apps",
		LogsDir:      "/var/log/vibefly",
		SeedDemoApps: false,
		Tunnel: TunnelConfig{
			Enabled:        false,
			Autostart:      false,
			Binary:         "cloudflared",
			Target:         "",
			StartupTimeout: 60 * time.Second,
		},
		RootfsBaseDir:     "",
		RootfsTarballPath: "",
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

	if cfg.Tunnel.Target == "" {
		cfg.Tunnel.Target = "http://" + cfg.Listen
	}
	return cfg, nil
}
