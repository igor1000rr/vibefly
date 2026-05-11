// Package main — точка входа агента VibeFly.
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
	"by.vibefly/agent/internal/logs"
	"by.vibefly/agent/internal/marketplace"
	"by.vibefly/agent/internal/metrics"
	"by.vibefly/agent/internal/server"
	"by.vibefly/agent/internal/supervisor"
)

var Version = "0.0.3-dev"

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
		logger.Warn("конфиг не найден, дефолты", "err", err)
		cfg = config.Default()
	}

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	sup := supervisor.New(logger, "/etc/systemd/system", cfg.AppsDir)
	logger.Info("supervisor", "available", sup.Available())

	logStreamer := logs.NewStreamer(logger, 500)
	appsStore := apps.NewStore(logger, sup)
	catalog := marketplace.New()

	// Фейк-логи только в demo-режиме.
	if !sup.Available() {
		appIDs := make([]string, 0, len(appsStore.List()))
		for _, a := range appsStore.List() {
			appIDs = append(appIDs, a.ID)
		}
		logStreamer.StartFakeGenerator(ctx, appIDs)
	}

	deps := server.Dependencies{
		Logger:      logger,
		Version:     Version,
		Metrics:     metrics.New(),
		Apps:        appsStore,
		Logs:        logStreamer,
		Supervisor:  sup,
		Marketplace: catalog,
		Token:       cfg.AuthToken,
	}

	srv := &http.Server{
		Addr:              cfg.Listen,
		Handler:           server.NewRouter(deps),
		ReadHeaderTimeout: 10 * time.Second,
	}

	go func() {
		logger.Info("агент стартует", "listen", cfg.Listen, "version", Version)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("http-сервер упал", "err", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	logger.Info("shutdown")
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("ошибка shutdown", "err", err)
	}
}
