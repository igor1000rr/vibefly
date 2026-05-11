// Package main — точка входа агента VibeFly.
//
// Агент живёт внутри Debian rootfs и слушает на 127.0.0.1:3001.
// Android-приложение общается с ним через эту локальную петлю.
package main

import (
	"context"
	"errors"
	"flag"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"by.vibefly/agent/internal/apps"
	"by.vibefly/agent/internal/config"
	"by.vibefly/agent/internal/metrics"
	"by.vibefly/agent/internal/server"
)

// Version подставляется через -ldflags на сборке.
var Version = "0.0.1-dev"

func main() {
	var (
		configPath = flag.String("config", "/etc/vibefly/agent.toml", "путь до TOML-конфига")
		showVer    = flag.Bool("version", false, "показать версию и выйти")
	)
	flag.Parse()

	if *showVer {
		os.Stdout.WriteString("vibefly-agent " + Version + "\n")
		return
	}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Warn("конфиг не найден, использую дефолты", "err", err, "path", *configPath)
		cfg = config.Default()
	}

	deps := server.Dependencies{
		Logger:  logger,
		Version: Version,
		Metrics: metrics.New(),
		Apps:    apps.NewStore(logger),
		Token:   cfg.AuthToken,
	}

	handler := server.NewRouter(deps)

	srv := &http.Server{
		Addr:              cfg.Listen,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	go func() {
		logger.Info("агент стартует", "listen", cfg.Listen, "version", Version)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("http-сервер упал", "err", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	logger.Info("получен сигнал остановки, gracefull shutdown")

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("ошибка при остановке сервера", "err", err)
	}
	logger.Info("агент остановлен")
}
