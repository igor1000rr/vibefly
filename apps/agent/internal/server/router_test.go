package server

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"by.vibefly/agent/internal/apps"
	"by.vibefly/agent/internal/logs"
	"by.vibefly/agent/internal/metrics"
)

func newTestDeps() Dependencies {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	return Dependencies{
		Logger:  logger,
		Version: "test",
		Metrics: metrics.New(),
		Apps:    apps.NewStore(logger),
		Logs:    logs.NewStreamer(logger, 100),
		Token:   "",
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
