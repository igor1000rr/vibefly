package server

import (
	"bytes"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"by.vibefly/agent/internal/apps"
	"by.vibefly/agent/internal/logs"
	"by.vibefly/agent/internal/metrics"
	"by.vibefly/agent/internal/supervisor"
)

func newTestDeps() Dependencies {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	sup := &supervisor.NopSupervisor{}
	return Dependencies{
		Logger:     logger,
		Version:    "test",
		Metrics:    metrics.New(),
		Apps:       apps.NewStore(logger, sup),
		Logs:       logs.NewStreamer(logger, 100),
		Supervisor: sup,
		Token:      "",
	}
}

func TestHealthEndpoint(t *testing.T) {
	handler := NewRouter(newTestDeps())

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("ожидался 200, получен %d", rec.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("не парсится JSON: %v", err)
	}
	if body["status"] != "ok" {
		t.Errorf("ожидался status=ok, получен %v", body["status"])
	}
	if body["supervisor_available"] != false {
		t.Errorf("ожидался supervisor_available=false, получен %v", body["supervisor_available"])
	}
}

func TestListAppsEndpoint(t *testing.T) {
	handler := NewRouter(newTestDeps())

	req := httptest.NewRequest(http.MethodGet, "/apps", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("ожидался 200, получен %d", rec.Code)
	}
	var list []map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &list); err != nil {
		t.Fatalf("не парсится JSON: %v", err)
	}
	if len(list) == 0 {
		t.Error("ожидались фейк-приложения")
	}
}

func TestRestartEndpoint(t *testing.T) {
	handler := NewRouter(newTestDeps())

	req := httptest.NewRequest(http.MethodPost, "/apps/amina-bot/restart", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("ожидался 200, получен %d", rec.Code)
	}
}

func TestStartEndpoint(t *testing.T) {
	handler := NewRouter(newTestDeps())

	req := httptest.NewRequest(http.MethodPost, "/apps/analytics-cron/start", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("ожидался 200, получен %d", rec.Code)
	}
}

func TestInstallEndpoint_Validation(t *testing.T) {
	// Без обязательных полей.
	handler := NewRouter(newTestDeps())

	body := bytes.NewBufferString(`{"id":""}`)
	req := httptest.NewRequest(http.MethodPost, "/apps", body)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("ожидался 400, получен %d", rec.Code)
	}
}

func TestInstallEndpoint_NopSupervisor(t *testing.T) {
	// С NopSupervisor install регистрирует app в store без обращения к systemd.
	handler := NewRouter(newTestDeps())

	body := bytes.NewBufferString(`{
		"id": "my-bot",
		"name": "My Bot",
		"start_cmd": "node index.js",
		"port": 5000
	}`)
	req := httptest.NewRequest(http.MethodPost, "/apps", body)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("ожидался 201, получен %d (body=%s)", rec.Code, rec.Body.String())
	}

	// Проверяем, что app появился в /apps.
	listReq := httptest.NewRequest(http.MethodGet, "/apps/my-bot", nil)
	listRec := httptest.NewRecorder()
	handler.ServeHTTP(listRec, listReq)
	if listRec.Code != http.StatusOK {
		t.Errorf("после install /apps/my-bot должен возвращать 200, получен %d", listRec.Code)
	}
}

func TestUninstallEndpoint(t *testing.T) {
	handler := NewRouter(newTestDeps())

	req := httptest.NewRequest(http.MethodDelete, "/apps/amina-bot", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("ожидался 200, получен %d", rec.Code)
	}
}

func TestLogsRecentEndpoint(t *testing.T) {
	deps := newTestDeps()
	deps.Logs.Append(logs.Entry{App: "amina-bot", Level: logs.LevelInfo, Message: "hello"})
	handler := NewRouter(deps)

	req := httptest.NewRequest(http.MethodGet, "/apps/amina-bot/logs?lines=10", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("ожидался 200, получен %d", rec.Code)
	}
}

func TestLogsRecentNotFound(t *testing.T) {
	handler := NewRouter(newTestDeps())

	req := httptest.NewRequest(http.MethodGet, "/apps/does-not-exist/logs", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("ожидался 404, получен %d", rec.Code)
	}
}

func TestAuthRequired(t *testing.T) {
	deps := newTestDeps()
	deps.Token = "secret"
	handler := NewRouter(deps)

	req := httptest.NewRequest(http.MethodGet, "/apps", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("ожидался 401, получен %d", rec.Code)
	}

	req = httptest.NewRequest(http.MethodGet, "/health", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Errorf("/health должен быть открытым, получен %d", rec.Code)
	}

	req = httptest.NewRequest(http.MethodGet, "/apps", nil)
	req.Header.Set("Authorization", "Bearer secret")
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Errorf("с токеном ожидался 200, получен %d", rec.Code)
	}
}

func TestClassifyLogLevel(t *testing.T) {
	cases := map[string]logs.Level{
		"INFO connected":           logs.LevelInfo,
		"WARN rate-limit":          logs.LevelWarn,
		"ERROR upstream timeout":   logs.LevelError,
		"FATAL panic":              logs.LevelError,
		"regular message":          logs.LevelInfo,
	}
	for input, want := range cases {
		if got := classifyLogLevel(input); got != want {
			t.Errorf("classifyLogLevel(%q) = %s, want %s", input, got, want)
		}
	}
}
