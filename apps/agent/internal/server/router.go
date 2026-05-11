// Package server — http-роутер и middleware агента.
package server

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"nhooyr.io/websocket"
	"nhooyr.io/websocket/wsjson"

	"by.vibefly/agent/internal/apps"
	"by.vibefly/agent/internal/logs"
	"by.vibefly/agent/internal/metrics"
)

// Dependencies — всё что нужно роутеру.
type Dependencies struct {
	Logger  *slog.Logger
	Version string
	Metrics metrics.Reader
	Apps    *apps.Store
	Logs    *logs.Streamer
	Token   string
}

// NewRouter возвращает http.Handler со всеми ручками.
func NewRouter(deps Dependencies) http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(slogRequestLogger(deps.Logger))
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(30 * time.Second))
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Authorization", "Content-Type"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	r.Get("/health", healthHandler(deps))

	r.Group(func(r chi.Router) {
		r.Use(authMiddleware(deps.Token))
		r.Get("/system", systemHandler(deps))
		r.Get("/apps", listAppsHandler(deps))
		r.Get("/apps/{id}", getAppHandler(deps))
		r.Post("/apps/{id}/restart", restartAppHandler(deps))
		r.Post("/apps/{id}/stop", stopAppHandler(deps))
		r.Get("/apps/{id}/logs", logsRecentHandler(deps))
		r.Get("/apps/{id}/logs/stream", logsStreamHandler(deps))
	})

	return r
}

func healthHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]any{
			"status":  "ok",
			"version": deps.Version,
			"time":    time.Now().UTC(),
		})
	}
}

func systemHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, deps.Metrics.Read())
	}
}

func listAppsHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, deps.Apps.List())
	}
}

func getAppHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		app, err := deps.Apps.Get(id)
		if err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, app)
	}
}

func restartAppHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if err := deps.Apps.Restart(id); err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "restarted", "id": id})
	}
}

func stopAppHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if err := deps.Apps.Stop(id); err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "stopped", "id": id})
	}
}

func logsRecentHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if _, err := deps.Apps.Get(id); err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		lines := 100
		if v := r.URL.Query().Get("lines"); v != "" {
			if n, err := strconv.Atoi(v); err == nil && n > 0 {
				lines = n
			}
		}
		writeJSON(w, http.StatusOK, deps.Logs.Recent(id, lines))
	}
}

func logsStreamHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if _, err := deps.Apps.Get(id); err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
			InsecureSkipVerify: true,
		})
		if err != nil {
			deps.Logger.Warn("ws upgrade failed", "err", err)
			return
		}
		ctx := r.Context()
		defer conn.Close(websocket.StatusNormalClosure, "")

		// Сначала отдаём недавние 100 записей как backlog.
		for _, e := range deps.Logs.Recent(id, 100) {
			if err := wsjson.Write(ctx, conn, e); err != nil {
				return
			}
		}

		sub := deps.Logs.Subscribe(ctx, id)
		for {
			select {
			case <-ctx.Done():
				return
			case e, ok := <-sub:
				if !ok {
					return
				}
				if err := wsjson.Write(ctx, conn, e); err != nil {
					return
				}
			}
		}
	}
}

func authMiddleware(token string) func(next http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if token == "" {
				next.ServeHTTP(w, r)
				return
			}
			header := r.Header.Get("Authorization")
			if !strings.HasPrefix(header, "Bearer ") || strings.TrimPrefix(header, "Bearer ") != token {
				writeError(w, http.StatusUnauthorized, "unauthorized")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func slogRequestLogger(logger *slog.Logger) func(next http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
			start := time.Now()

			next.ServeHTTP(ww, r)

			// WebSocket upgrade всегда 101; логируем как long-poll.
			logger.Info("http",
				"method", r.Method,
				"path", r.URL.Path,
				"status", ww.Status(),
				"bytes", ww.BytesWritten(),
				"dur_ms", time.Since(start).Milliseconds(),
				"req_id", middleware.GetReqID(r.Context()),
			)
		})
	}
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

// errAppNotFound — обёртка для ясных логов.
var errAppNotFound = errors.New("app not found")
