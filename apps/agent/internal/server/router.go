// Package server — http-роутер и middleware агента.
package server

import (
	"encoding/json"
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
	"by.vibefly/agent/internal/supervisor"
)

// Dependencies — всё что нужно роутеру.
type Dependencies struct {
	Logger     *slog.Logger
	Version    string
	Metrics    metrics.Reader
	Apps       *apps.Store
	Logs       *logs.Streamer
	Supervisor supervisor.Supervisor
	Token      string
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
		r.Post("/apps", installAppHandler(deps))
		r.Get("/apps/{id}", getAppHandler(deps))
		r.Delete("/apps/{id}", uninstallAppHandler(deps))
		r.Post("/apps/{id}/start", startAppHandler(deps))
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
			"status":                "ok",
			"version":               deps.Version,
			"time":                  time.Now().UTC(),
			"supervisor_available":  deps.Supervisor != nil && deps.Supervisor.Available(),
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

// installRequest — тело POST /apps.
type installRequest struct {
	ID         string            `json:"id"`
	Name       string            `json:"name"`
	WorkingDir string            `json:"working_dir"`
	StartCmd   string            `json:"start_cmd"`
	Env        map[string]string `json:"env"`
	MemoryMax  string            `json:"memory_max"`
	CPUQuota   string            `json:"cpu_quota"`
	Repo       string            `json:"repo"`
	Branch     string            `json:"branch"`
	Port       int               `json:"port"`
	Domain     string            `json:"domain"`
}

func installAppHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req installRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid JSON: "+err.Error())
			return
		}
		if req.ID == "" || req.StartCmd == "" {
			writeError(w, http.StatusBadRequest, "id и start_cmd обязательны")
			return
		}
		spec := supervisor.AppSpec{
			ID:         req.ID,
			Name:       firstNonEmpty(req.Name, req.ID),
			WorkingDir: req.WorkingDir,
			StartCmd:   req.StartCmd,
			Env:        req.Env,
			MemoryMax:  req.MemoryMax,
			CPUQuota:   req.CPUQuota,
		}
		meta := apps.App{
			ID:     req.ID,
			Name:   firstNonEmpty(req.Name, req.ID),
			Repo:   req.Repo,
			Branch: req.Branch,
			Port:   req.Port,
			Domain: req.Domain,
			Status: apps.StatusStopped,
		}
		if err := deps.Apps.Install(spec, meta); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusCreated, map[string]string{"status": "installed", "id": req.ID})
	}
}

func uninstallAppHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if err := deps.Apps.Uninstall(id); err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "uninstalled", "id": id})
	}
}

func startAppHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if err := deps.Apps.Start(id); err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "started", "id": id})
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

// logsStreamHandler отдаёт лайв-логи. Если supervisor доступен — использует
// journalctl -fu через supervisor.FollowLogs, иначе fallback на in-memory streamer.
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

		// Backlog из ring-buffer (в нём и фейк, и реальные если кто-то писал в Streamer).
		for _, e := range deps.Logs.Recent(id, 100) {
			if err := wsjson.Write(ctx, conn, e); err != nil {
				return
			}
		}

		// Если supervisor доступен — берём лайв-поток journalctl.
		if deps.Supervisor != nil && deps.Supervisor.Available() {
			follow, err := deps.Supervisor.FollowLogs(ctx, id)
			if err != nil {
				deps.Logger.Warn("follow logs failed", "id", id, "err", err)
				return
			}
			for line := range follow {
				entry := logs.Entry{
					Time:    time.Now().UTC(),
					App:     id,
					Level:   classifyLogLevel(line),
					Source:  "journal",
					Message: line,
				}
				if err := wsjson.Write(ctx, conn, entry); err != nil {
					return
				}
			}
			return
		}

		// Fallback на in-memory pub/sub.
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

// classifyLogLevel — грубая эвристика для подкраски строк journalctl.
func classifyLogLevel(line string) logs.Level {
	lower := strings.ToLower(line)
	switch {
	case strings.Contains(lower, "error") || strings.Contains(lower, "panic") || strings.Contains(lower, "fatal"):
		return logs.LevelError
	case strings.Contains(lower, "warn"):
		return logs.LevelWarn
	default:
		return logs.LevelInfo
	}
}

func firstNonEmpty(a, b string) string {
	if a != "" {
		return a
	}
	return b
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
