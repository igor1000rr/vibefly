// Package server — http-роутер и middleware агента.
package server

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"nhooyr.io/websocket"
	"nhooyr.io/websocket/wsjson"

	"by.vibefly/agent/internal/apps"
	"by.vibefly/agent/internal/binstore"
	"by.vibefly/agent/internal/logs"
	"by.vibefly/agent/internal/manifest"
	"by.vibefly/agent/internal/marketplace"
	"by.vibefly/agent/internal/metrics"
	"by.vibefly/agent/internal/supervisor"
	"by.vibefly/agent/internal/tunnel"
)

type Dependencies struct {
	Logger      *slog.Logger
	Version     string
	Metrics     metrics.Reader
	Apps        *apps.Store
	Logs        *logs.Streamer
	Supervisor  supervisor.Supervisor
	Marketplace *marketplace.Catalog
	Tunnel      tunnel.Manager
	AppTunnels  *tunnel.AppTunnels
	Token       string
	AppsDir     string
}

func NewRouter(deps Dependencies) http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(slogRequestLogger(deps.Logger))
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(120 * time.Second))
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
		r.Post("/apps/from-repo", installFromRepoHandler(deps))
		r.Get("/apps/{id}", getAppHandler(deps))
		r.Delete("/apps/{id}", uninstallAppHandler(deps))
		r.Post("/apps/{id}/start", startAppHandler(deps))
		r.Post("/apps/{id}/restart", restartAppHandler(deps))
		r.Post("/apps/{id}/stop", stopAppHandler(deps))
		r.Get("/apps/{id}/logs", logsRecentHandler(deps))
		r.Get("/apps/{id}/logs/stream", logsStreamHandler(deps))

		r.Get("/apps/{id}/tunnel", appTunnelStatusHandler(deps))
		r.Post("/apps/{id}/publish", appTunnelPublishHandler(deps))
		r.Delete("/apps/{id}/publish", appTunnelUnpublishHandler(deps))

		r.Get("/marketplace", marketplaceListHandler(deps))
		r.Get("/marketplace/{id}", marketplaceGetHandler(deps))
		r.Post("/marketplace/{id}/install", marketplaceInstallHandler(deps))

		r.Get("/tunnel", tunnelStatusHandler(deps))
		r.Post("/tunnel/start", tunnelStartHandler(deps))
		r.Post("/tunnel/stop", tunnelStopHandler(deps))

		// Превью манифеста (без инсталла) — для UI чтобы показать юзеру поля перед Deploy.
		r.Get("/manifest/preview", manifestPreviewHandler(deps))
	})

	return r
}

func healthHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]any{
			"status":               "ok",
			"version":              deps.Version,
			"time":                 time.Now().UTC(),
			"supervisor_available": deps.Supervisor != nil && deps.Supervisor.Available(),
			"tunnel_available":     deps.Tunnel != nil,
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
		writeJSON(w, http.StatusOK, withTunnelURLs(deps, deps.Apps.List()))
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
		writeJSON(w, http.StatusOK, withTunnelURL(deps, app))
	}
}

func withTunnelURL(deps Dependencies, app *apps.App) *apps.App {
	if deps.AppTunnels != nil && app != nil {
		app.PublicURL = deps.AppTunnels.PublicURL(app.ID)
	}
	return app
}

func withTunnelURLs(deps Dependencies, list []*apps.App) []*apps.App {
	if deps.AppTunnels == nil {
		return list
	}
	for _, a := range list {
		a.PublicURL = deps.AppTunnels.PublicURL(a.ID)
	}
	return list
}

func appTunnelStatusHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if deps.AppTunnels == nil {
			writeJSON(w, http.StatusOK, tunnel.Status{Provider: tunnel.ProviderNone})
			return
		}
		writeJSON(w, http.StatusOK, deps.AppTunnels.Status(id))
	}
}

func appTunnelPublishHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if deps.AppTunnels == nil {
			writeError(w, http.StatusServiceUnavailable, "app tunnels not configured (cloudflared не доступен)")
			return
		}
		app, err := deps.Apps.Get(id)
		if err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}
		if app.Port == 0 {
			writeError(w, http.StatusBadRequest, "у приложения не указан port — публикация невозможна")
			return
		}
		status, err := deps.AppTunnels.Publish(r.Context(), id, app.Port)
		if err != nil {
			writeJSON(w, http.StatusInternalServerError, map[string]any{
				"error":  err.Error(),
				"status": status,
			})
			return
		}
		writeJSON(w, http.StatusOK, status)
	}
}

func appTunnelUnpublishHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if deps.AppTunnels == nil {
			writeError(w, http.StatusServiceUnavailable, "app tunnels not configured")
			return
		}
		if err := deps.AppTunnels.Unpublish(id); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "unpublished", "id": id})
	}
}

// installRequest — ручной деплой с binary_url или archive_url.
// Раньше binary_url понимался строго как бинарь. Теперь если URL заканчивается
// на .tar.gz/.tgz/.zip — распаковываем в workdir.
type installRequest struct {
	ID            string            `json:"id"`
	Name          string            `json:"name"`
	WorkingDir    string            `json:"working_dir"`
	StartCmd      string            `json:"start_cmd"`
	Env           map[string]string `json:"env"`
	MemoryMax     string            `json:"memory_max"`
	CPUQuota      string            `json:"cpu_quota"`
	Repo          string            `json:"repo"`
	Branch        string            `json:"branch"`
	Port          int               `json:"port"`
	Domain        string            `json:"domain"`
	BinaryURL     string            `json:"binary_url"`
	Autostart     bool              `json:"autostart"`
	RestartPolicy string            `json:"restart_policy"`
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
		if err := installFromRequest(r.Context(), deps, req); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusCreated, map[string]string{"status": "installed", "id": req.ID})
	}
}

func installFromRequest(ctx context.Context, deps Dependencies, req installRequest) error {
	workdir := req.WorkingDir
	if workdir == "" && deps.AppsDir != "" {
		workdir = filepath.Join(deps.AppsDir, req.ID)
	}

	if req.BinaryURL != "" {
		if workdir == "" {
			return fmt.Errorf("binary_url указан, но working_dir/apps_dir не определён")
		}
		if err := downloadOrExtract(ctx, deps.Logger, req.ID, req.BinaryURL, workdir); err != nil {
			return err
		}
	}

	spec := supervisor.AppSpec{
		ID:            req.ID,
		Name:          firstNonEmpty(req.Name, req.ID),
		WorkingDir:    workdir,
		StartCmd:      req.StartCmd,
		Env:           req.Env,
		MemoryMax:     req.MemoryMax,
		CPUQuota:      req.CPUQuota,
		Autostart:     req.Autostart,
		RestartPolicy: req.RestartPolicy,
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
	return deps.Apps.Install(spec, meta)
}

// downloadOrExtract — если URL это .tar.gz/.zip — распаковываем. Иначе скачиваем в workdir/binary.
func downloadOrExtract(ctx context.Context, logger *slog.Logger, appID, fileURL, workdir string) error {
	dl := binstore.New()
	dlCtx, cancel := context.WithTimeout(ctx, binstore.DefaultTimeout)
	defer cancel()

	if kind := binstore.DetectArchive(fileURL); kind != "" {
		logger.Info("скачиваю архив", "id", appID, "url", fileURL, "kind", kind)
		exec, err := dl.DownloadAndExtract(dlCtx, fileURL, workdir)
		if err != nil {
			return fmt.Errorf("download+extract: %w", err)
		}
		logger.Info("архив распакован", "id", appID, "executable", exec, "workdir", workdir)
		return nil
	}
	logger.Info("скачиваю бинарь", "id", appID, "url", fileURL)
	path, err := dl.DownloadBinary(dlCtx, fileURL, workdir)
	if err != nil {
		return fmt.Errorf("download binary: %w", err)
	}
	logger.Info("бинарь скачан", "id", appID, "path", path)
	return nil
}

// installFromRepoRequest — деплой по ссылке на репо. Агент сам тянет vibefly.toml.
type installFromRepoRequest struct {
	RepoURL string `json:"repo_url"` // https://github.com/owner/repo
	Branch  string `json:"branch"`   // "" → main потом master
	ID      string `json:"id"`       // override (default = repo name)
}

func installFromRepoHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req installFromRepoRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid JSON: "+err.Error())
			return
		}
		if req.RepoURL == "" {
			writeError(w, http.StatusBadRequest, "repo_url обязателен")
			return
		}
		owner, repo, err := manifest.ParseGitHubURL(req.RepoURL)
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		m, branch, err := manifest.FetchFromRepoURL(r.Context(), req.RepoURL, req.Branch)
		if err != nil {
			writeError(w, http.StatusBadRequest, "не удалось взять vibefly.toml: "+err.Error())
			return
		}

		appID := req.ID
		if appID == "" {
			appID = sanitizeID(repo)
		}

		internalReq := installRequest{
			ID:            appID,
			Name:          firstNonEmpty(m.Name, repo),
			StartCmd:      m.StartCmd,
			Env:           m.Env,
			MemoryMax:     m.MemoryMax,
			CPUQuota:      m.CPUQuota,
			Repo:          owner + "/" + repo,
			Branch:        branch,
			Port:          m.Port,
			BinaryURL:     m.BinaryURL,
			Autostart:     m.Autostart,
			RestartPolicy: m.RestartPolicy,
		}
		if internalReq.StartCmd == "" {
			internalReq.StartCmd = "./binary"
		}
		if err := installFromRequest(r.Context(), deps, internalReq); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusCreated, map[string]any{
			"status":    "installed",
			"id":        appID,
			"repo":      owner + "/" + repo,
			"branch":    branch,
			"manifest":  m,
		})
	}
}

// manifestPreviewHandler — GET /manifest/preview?repo_url=...&branch=...
// Для UI: выводит поля манифеста без инсталла, чтобы юзер подтвердил перед Deploy.
func manifestPreviewHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		repoURL := r.URL.Query().Get("repo_url")
		branch := r.URL.Query().Get("branch")
		if repoURL == "" {
			writeError(w, http.StatusBadRequest, "repo_url обязателен")
			return
		}
		m, foundBranch, err := manifest.FetchFromRepoURL(r.Context(), repoURL, branch)
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"manifest": m,
			"branch":   foundBranch,
		})
	}
}

// sanitizeID — из имени репо делает валидный app ID.
func sanitizeID(s string) string {
	var b strings.Builder
	for _, r := range s {
		switch {
		case r >= 'a' && r <= 'z', r >= '0' && r <= '9', r == '-', r == '_':
			b.WriteRune(r)
		case r >= 'A' && r <= 'Z':
			b.WriteRune(r + ('a' - 'A'))
		case r == ' ', r == '.':
			b.WriteRune('-')
		}
	}
	result := b.String()
	if result == "" {
		result = "app"
	}
	return result
}

func uninstallAppHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if deps.AppTunnels != nil {
			_ = deps.AppTunnels.Unpublish(id)
		}
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

		for _, e := range deps.Logs.Recent(id, 100) {
			if err := wsjson.Write(ctx, conn, e); err != nil {
				return
			}
		}

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

func marketplaceListHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) {
		if deps.Marketplace == nil {
			writeJSON(w, http.StatusOK, []marketplace.Template{})
			return
		}
		writeJSON(w, http.StatusOK, deps.Marketplace.List())
	}
}

func marketplaceGetHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := chi.URLParam(r, "id")
		if deps.Marketplace == nil {
			writeError(w, http.StatusNotFound, "marketplace unavailable")
			return
		}
		tpl, ok := deps.Marketplace.Get(id)
		if !ok {
			writeError(w, http.StatusNotFound, "template not found")
			return
		}
		writeJSON(w, http.StatusOK, tpl)
	}
}

type marketplaceInstallRequest struct {
	AppID  string            `json:"app_id"`
	Domain string            `json:"domain"`
	Port   int               `json:"port"`
	Env    map[string]string `json:"env"`
}

func marketplaceInstallHandler(deps Dependencies) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		templateID := chi.URLParam(r, "id")
		if deps.Marketplace == nil {
			writeError(w, http.StatusNotFound, "marketplace unavailable")
			return
		}
		tpl, ok := deps.Marketplace.Get(templateID)
		if !ok {
			writeError(w, http.StatusNotFound, "template not found")
			return
		}

		var req marketplaceInstallRequest
		_ = json.NewDecoder(r.Body).Decode(&req)

		appID := firstNonEmpty(req.AppID, tpl.ID)
		port := req.Port
		if port == 0 {
			port = tpl.DefaultPort
		}

		spec := supervisor.AppSpec{
			ID:        appID,
			Name:      tpl.Name,
			StartCmd:  tpl.StartCmd,
			Env:       req.Env,
			MemoryMax: tpl.MemoryMax,
		}
		meta := apps.App{
			ID:     appID,
			Name:   tpl.Name,
			Repo:   tpl.Repo,
			Port:   port,
			Domain: req.Domain,
			Status: apps.StatusStopped,
		}
		if err := deps.Apps.Install(spec, meta); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusCreated, map[string]any{
			"status":     "installed",
			"id":         appID,
			"template":   templateID,
			"supervisor": deps.Supervisor != nil && deps.Supervisor.Available(),
		})
	}
}

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
