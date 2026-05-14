package tunnel

import (
	"context"
	"fmt"
	"log/slog"
	"sync"
)

// AppTunnels — менеджер персональных cloudflared-туннелей на приложение.
//
// Основной deps.Tunnel проксирует :3001 (сам агент) — для remote-управления.
// AppTunnels проксирует пользовательские приложения (например :8080 hello-app) —
// даёт каждому свой https://*.trycloudflare.com URL.
//
// Каждый tunnel это отдельный cloudflared-процесс — ~25-30 MB RAM на каждый.
// На телефоне с 4 GB RAM это легко выдерживает 5-10 параллельных туннелей.
type AppTunnels struct {
	logger *slog.Logger
	binary string

	mu      sync.Mutex
	active  map[string]*Cloudflared // app_id → tunnel
}

func NewAppTunnels(logger *slog.Logger, binary string) *AppTunnels {
	if binary == "" {
		binary = "cloudflared"
	}
	return &AppTunnels{
		logger: logger.With("component", "app-tunnels"),
		binary: binary,
		active: make(map[string]*Cloudflared),
	}
}

// Publish — стартует туннель на 127.0.0.1:port для приложения appID.
// Если уже публикуемый — возвращает текущий Status (идемпотентно).
func (a *AppTunnels) Publish(ctx context.Context, appID string, port int) (Status, error) {
	if port <= 0 {
		return Status{Provider: ProviderNone}, fmt.Errorf("publish: у приложения %q не указан port", appID)
	}

	a.mu.Lock()
	if existing, ok := a.active[appID]; ok {
		s := existing.Status()
		a.mu.Unlock()
		if s.Active {
			return s, nil
		}
	}
	a.mu.Unlock()

	target := fmt.Sprintf("http://127.0.0.1:%d", port)
	tun := NewCloudflared(CloudflaredOptions{
		Logger:    a.logger.With("app", appID),
		Binary:    a.binary,
		TargetURL: target,
	})

	a.mu.Lock()
	a.active[appID] = tun
	a.mu.Unlock()

	status, err := tun.Start(ctx)
	if err != nil {
		a.mu.Lock()
		delete(a.active, appID)
		a.mu.Unlock()
		return status, err
	}
	a.logger.Info("app published", "id", appID, "url", status.PublicURL, "target", target)
	return status, nil
}

// Unpublish — останавливает туннель приложения. Идемпотентно.
func (a *AppTunnels) Unpublish(appID string) error {
	a.mu.Lock()
	tun, ok := a.active[appID]
	delete(a.active, appID)
	a.mu.Unlock()
	if !ok {
		return nil
	}
	a.logger.Info("app unpublished", "id", appID)
	return tun.Close()
}

// Status — снимок состояния туннеля для приложения. Если не опубликовано — Active=false.
func (a *AppTunnels) Status(appID string) Status {
	a.mu.Lock()
	tun, ok := a.active[appID]
	a.mu.Unlock()
	if !ok {
		return Status{Provider: ProviderNone}
	}
	return tun.Status()
}

// PublicURL — быстрый helper для Store.List(): возвращает "" если нет или не активен.
func (a *AppTunnels) PublicURL(appID string) string {
	s := a.Status(appID)
	if !s.Active {
		return ""
	}
	return s.PublicURL
}

// CloseAll — вызывается при shutdown агента. Гасит все туннели.
func (a *AppTunnels) CloseAll() error {
	a.mu.Lock()
	tunnels := a.active
	a.active = make(map[string]*Cloudflared)
	a.mu.Unlock()
	for id, tun := range tunnels {
		if err := tun.Close(); err != nil {
			a.logger.Warn("close app tunnel", "id", id, "err", err)
		}
	}
	return nil
}
