package server

import (
	"net/http"

	"by.vibefly/agent/internal/tunnel"
)

// HTTP handlers для управления Cloudflare Tunnel.
//
// Эндпоинты:
//   GET  /tunnel        — текущий статус (active, public_url, started_at, ...)
//   POST /tunnel/start  — запустить туннель, ждать публичный URL (~5-30 сек)
//   POST /tunnel/stop   — остановить (SIGTERM + 5s + SIGKILL)
//
// Все три защищены authMiddleware, как и остальной API.

func tunnelStatusHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		if deps.Tunnel == nil {
			writeJSON(w, http.StatusOK, tunnel.Status{Provider: tunnel.ProviderNone})
			return
		}
		writeJSON(w, http.StatusOK, deps.Tunnel.Status())
	}
}

func tunnelStartHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if deps.Tunnel == nil {
			writeError(w, http.StatusServiceUnavailable, "tunnel: not configured")
			return
		}
		status, err := deps.Tunnel.Start(r.Context())
		if err != nil {
			deps.Logger.Warn("tunnel start failed", "err", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{
				"error":  err.Error(),
				"status": status,
			})
			return
		}
		writeJSON(w, http.StatusOK, status)
	}
}

func tunnelStopHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		if deps.Tunnel == nil {
			writeError(w, http.StatusServiceUnavailable, "tunnel: not configured")
			return
		}
		if err := deps.Tunnel.Stop(); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, deps.Tunnel.Status())
	}
}
