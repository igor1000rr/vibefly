// Package apps — управление пользовательскими приложениями.
//
// В фазе 1 здесь будет интеграция с systemd через journalctl/systemctl и
// внутренний supervisor. Сейчас — in-memory store с фейк-данными, чтобы UI
// можно было разрабатывать и тестировать.
package apps

import (
	"errors"
	"log/slog"
	"sort"
	"sync"
	"time"
)

// Status — статус приложения.
type Status string

const (
	StatusRunning   Status = "running"
	StatusStopped   Status = "stopped"
	StatusDeploying Status = "deploying"
	StatusFailed    Status = "failed"
)

// App описывает приложение в системе.
type App struct {
	ID         string    `json:"id"`
	Name       string    `json:"name"`
	Status     Status    `json:"status"`
	Repo       string    `json:"repo,omitempty"`
	Branch     string    `json:"branch,omitempty"`
	Port       int       `json:"port,omitempty"`
	Domain     string    `json:"domain,omitempty"`
	MemoryMB   int       `json:"memory_mb,omitempty"`
	StartedAt  time.Time `json:"started_at,omitempty"`
	LastDeploy time.Time `json:"last_deploy,omitempty"`
}

// ErrNotFound — приложение с таким id не найдено.
var ErrNotFound = errors.New("app not found")

// Store держит список приложений.
type Store struct {
	mu     sync.RWMutex
	items  map[string]*App
	logger *slog.Logger
}

// NewStore создаёт хранилище и наполняет его фейк-данными для UI-разработки.
func NewStore(logger *slog.Logger) *Store {
	s := &Store{
		items:  make(map[string]*App),
		logger: logger,
	}
	s.seedFakes()
	return s
}

func (s *Store) seedFakes() {
	now := time.Now().UTC()
	fakes := []*App{
		{
			ID: "amina-bot", Name: "amina-bot", Status: StatusRunning,
			Repo: "antsincgame/Amina-bot", Branch: "main", Port: 3001,
			Domain: "@AIAMINABOT", MemoryMB: 124,
			StartedAt: now.Add(-86*time.Hour - 14*time.Minute), LastDeploy: now.Add(-2 * time.Hour),
		},
		{
			ID: "tonforge-api", Name: "tonforge-api", Status: StatusRunning,
			Repo: "antsincgame/tonforge", Branch: "main", Port: 4001,
			Domain: "api.tonforge.org", MemoryMB: 89,
			StartedAt: now.Add(-12*time.Hour - 4*time.Minute), LastDeploy: now.Add(-12 * time.Hour),
		},
		{
			ID: "azcrm-staging", Name: "azcrm-staging", Status: StatusDeploying,
			Repo: "igor1000rr/azcrm", Branch: "develop", Port: 4002,
			Domain: "staging.azgroup.net",
		},
		{
			ID: "analytics-cron", Name: "analytics-cron", Status: StatusStopped,
			Repo: "igor1000rr/analytics", Branch: "main",
		},
	}
	for _, a := range fakes {
		s.items[a.ID] = a
	}
}

// List возвращает все приложения отсортированные по имени.
func (s *Store) List() []*App {
	s.mu.RLock()
	defer s.mu.RUnlock()

	out := make([]*App, 0, len(s.items))
	for _, a := range s.items {
		out = append(out, a)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out
}

// Get возвращает приложение по id.
func (s *Store) Get(id string) (*App, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	app, ok := s.items[id]
	if !ok {
		return nil, ErrNotFound
	}
	return app, nil
}

// Restart переводит приложение в running. Заглушка — реальный рестарт через
// systemctl будет в фазе 1.
func (s *Store) Restart(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	app, ok := s.items[id]
	if !ok {
		return ErrNotFound
	}
	app.Status = StatusRunning
	app.StartedAt = time.Now().UTC()
	s.logger.Info("app restarted", "id", id)
	return nil
}

// Stop останавливает приложение.
func (s *Store) Stop(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	app, ok := s.items[id]
	if !ok {
		return ErrNotFound
	}
	app.Status = StatusStopped
	s.logger.Info("app stopped", "id", id)
	return nil
}
