package apps

import (
	"io"
	"log/slog"
	"testing"

	"by.vibefly/agent/internal/supervisor"
)

func newTestStore() *Store {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	// NopSupervisor — store работает на фейк-данных.
	return NewStore(logger, &supervisor.NopSupervisor{})
}

func TestStore_List(t *testing.T) {
	s := newTestStore()
	items := s.List()
	if len(items) == 0 {
		t.Fatal("ожидались фейк-приложения")
	}
	// Проверяем сортировку по имени.
	for i := 1; i < len(items); i++ {
		if items[i-1].Name > items[i].Name {
			t.Errorf("список не отсортирован: %q > %q", items[i-1].Name, items[i].Name)
		}
	}
}

func TestStore_Get(t *testing.T) {
	s := newTestStore()

	app, err := s.Get("amina-bot")
	if err != nil {
		t.Fatalf("неожиданная ошибка: %v", err)
	}
	if app.ID != "amina-bot" {
		t.Errorf("ожидался amina-bot, получен %q", app.ID)
	}

	if _, err := s.Get("несуществующий"); err != ErrNotFound {
		t.Errorf("ожидался ErrNotFound, получен %v", err)
	}
}

func TestStore_Restart(t *testing.T) {
	s := newTestStore()

	app, _ := s.Get("analytics-cron")
	if app.Status != StatusStopped {
		t.Fatalf("предусловие нарушено: ожидался stopped, получен %s", app.Status)
	}

	if err := s.Restart("analytics-cron"); err != nil {
		t.Fatalf("неожиданная ошибка: %v", err)
	}

	app, _ = s.Get("analytics-cron")
	if app.Status != StatusRunning {
		t.Errorf("после рестарта ожидался running, получен %s", app.Status)
	}
}

func TestStore_Stop(t *testing.T) {
	s := newTestStore()

	if err := s.Stop("amina-bot"); err != nil {
		t.Fatalf("неожиданная ошибка: %v", err)
	}
	app, _ := s.Get("amina-bot")
	if app.Status != StatusStopped {
		t.Errorf("ожидался stopped, получен %s", app.Status)
	}
}

func TestStore_SupervisorAvailable(t *testing.T) {
	s := newTestStore()
	if s.SupervisorAvailable() {
		t.Error("NopSupervisor.Available() должен возвращать false")
	}
}
