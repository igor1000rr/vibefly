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
	"by.vibefly/agent/internal/tunnel"
)

var Version = "0.0.8-dev"

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

	// Streamer создаётся РАНЬШЕ supervisor'а, чтобы передать его как logSink.
	logStreamer := logs.NewStreamer(logger, 500)
	sup := supervisor.New(logger, "/etc/systemd/system", cfg.AppsDir, logStreamer)
	logger.Info("supervisor", "available", sup.Available(), "seed_demo_apps", cfg.SeedDemoApps)

	appsStore := apps.NewStoreWithDir(logger, sup, cfg.AppsDir, cfg.SeedDemoApps)
	catalog := marketplace.New()

	var tun tunnel.Manager
	if cfg.Tunnel.Enabled {
		tun = tunnel.NewCloudflared(tunnel.CloudflaredOptions{
			Logger:         logger.With("component", "tunnel"),
			Binary:         cfg.Tunnel.Binary,
			TargetURL:      cfg.Tunnel.Target,
			StartupTimeout: cfg.Tunnel.StartupTimeout,
		})
		logger.Info("tunnel", "enabled", true, "target", cfg.Tunnel.Target)

		if cfg.Tunnel.Autostart {
			go func() {
				startCtx, startCancel := context.WithTimeout(context.Background(), cfg.Tunnel.StartupTimeout+10*time.Second)
				defer startCancel()
				if _, err := tun.Start(startCtx); err != nil {
					logger.Warn("tunnel autostart failed", "err", err)
				}
			}()
		}
	}

	if !sup.Available() && cfg.SeedDemoApps {
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
		Tunnel:      tun,
		Token:       cfg.AuthToken,
		AppsDir:     cfg.AppsDir,
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

	// Autostart всех приложений с spec.Autostart=true. В отдельной горутине, с
	// небольшой задержкой — даём HTTP-серверу подняться и tunnel autostart
	// (если включен) запуститься первым. Падение одного приложения не блокирует
	// остальные — это внутри Store.AutostartAll().
	go func() {
		time.Sleep(2 * time.Second)
		appsStore.AutostartAll()
	}()

	<-ctx.Done()
	logger.Info("shutdown")
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()
	if tun != nil {
		_ = tun.Close()
	}
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("ошибка shutdown", "err", err)
	}
}
