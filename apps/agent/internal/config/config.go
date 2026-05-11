// Package config — загрузка конфигурации агента из TOML.
package config

import (
	"errors"
	"fmt"
	"os"

	"github.com/pelletier/go-toml/v2"
)

// Config — финальная конфигурация агента.
type Config struct {
	// Listen — адрес для http-сервера. Дефолт 127.0.0.1:3001.
	Listen string `toml:"listen"`

	// AuthToken — bearer-токен, который Android-приложение шлёт в заголовке.
	// Если пустой — авторизация отключена (только для разработки).
	AuthToken string `toml:"auth_token"`

	// AppsDir — где хранятся пользовательские приложения внутри rootfs.
	AppsDir string `toml:"apps_dir"`

	// LogsDir — где хранятся файловые логи приложений.
	LogsDir string `toml:"logs_dir"`
}

// Default возвращает дефолтную конфигурацию.
func Default() Config {
	return Config{
		Listen:    "127.0.0.1:3001",
		AuthToken: "",
		AppsDir:   "/var/lib/vibefly/apps",
		LogsDir:   "/var/log/vibefly",
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
	return cfg, nil
}
