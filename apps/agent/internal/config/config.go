// Package config — загрузка конфигурации агента из TOML.
package config

import (
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/pelletier/go-toml/v2"
)

type TunnelConfig struct {
	Enabled        bool          `toml:"enabled"`
	Autostart      bool          `toml:"autostart"`
	Binary         string        `toml:"binary"`
	Target         string        `toml:"target"`
	StartupTimeout time.Duration `toml:"startup_timeout"`
}

// SSHConfig — встроенный ssh-демон.
//
// Идея: один sshd на весь агент, подключившись один раз пользователь попадает в
// shell с cwd=apps_dir — оттуда видны все приложения как папки. Per-app SSH не нужен —
// это избыточная сложность, всё одно на одном телефоне живёт в одном sandbox'е.
//
// Listen по умолчанию 127.0.0.1:2222. Чтобы из LAN или через tunnel — нужен 0.0.0.0:2222
// + tunnel TCP mode (cloudflared --proto h2; или Tailscale). Для Tailscale хватит LAN-режима.
type SSHConfig struct {
	Enabled       bool   `toml:"enabled"`
	Listen        string `toml:"listen"`         // 127.0.0.1:2222 / 0.0.0.0:2222
	HostKeyPath   string `toml:"host_key_path"`  // ed25519 host key (создаётся при первом старте)
	AuthKeysPath  string `toml:"auth_keys_path"` // файл с authorized_keys (OpenSSH формат)
	Password      string `toml:"password"`       // fallback для первого старта (откл. при "")
	Shell         string `toml:"shell"`          // /system/bin/sh на Android
}

type Config struct {
	Listen    string `toml:"listen"`
	AuthToken string `toml:"auth_token"`
	AppsDir   string `toml:"apps_dir"`
	LogsDir   string `toml:"logs_dir"`

	SeedDemoApps bool `toml:"seed_demo_apps"`

	Tunnel TunnelConfig `toml:"tunnel"`
	SSH    SSHConfig    `toml:"ssh"`
}

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
		SSH: SSHConfig{
			Enabled:      false, // опт-ин из UI, по умолчанию выкл
			Listen:       "0.0.0.0:2222", // 0.0.0.0 чтобы LAN и tailscale видели
			HostKeyPath:  "", // ставится в main.go: cfg.AppsDir + /../.ssh/host_ed25519
			AuthKeysPath: "", // то же директория рядом
			Password:     "",
			Shell:        "", // автодетект /system/bin/sh → /bin/sh
		},
	}
}

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
