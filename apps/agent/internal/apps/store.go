// Package apps — управление пользовательскими приложениями.
//
// Store держит метаданные приложений в памяти, а реальные операции
// (start/stop/restart, статус, логи) делегирует supervisor'у. На non-Linux
// или там, где systemd недоступен, supervisor — это NopSupervisor.
package apps

import (
	"context"
	"errors"
	"log/slog"
	"sort"
	"sync"
	"time"

	"by.vibefly/agent/internal/supervisor"
)

// Status — статус приложения.
type Status string

const (
	StatusRunning   Status = "running"
	StatusStopped   Status = "stopped"
	StatusDeploying Status = "deploying"
	StatusFailed    Status = "failed"
	StatusUnknown   Status = "unknown"
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
	StartCmd   string    `json:"start_cmd,omitempty"`
	StartedAt  time.Time `json:"started_at,omitempty"`
	LastDeploy time.Time `json:"last_deploy,omitempty"`
}

// ErrNotFound — приложение с таким id не найдено.
var ErrNotFound = errors.New("app not found")

// Store держит список приложений.
type Store struct {
	mu         sync.RWMutex
	items      map[string]*App
	logger     *slog.Logger
	supervisor supervisor.Supervisor
}

// NewStore создаёт хранилище. Если supervisor.Available() — статусы
// синхронизируются с systemd. Иначе Store пустой (или с фейк-данными,
// если seedDemo=true).
func NewStore(logger *slog.Logger, sup supervisor.Supervisor, seedDemo bool) *Store {
	s := &Store{
		items:      make(map[string]*App),
		logger:     logger,
		supervisor: sup,
	}
	if !sup.Available() && seedDemo {
		s.seedFakes()
	}
	return s
}

// SupervisorAvailable — фасад для UI: показать ли реальное состояние или demo-mode.
func (s *Store) SupervisorAvailable() bool { return s.supervisor.Available() }

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
	items := make([]*App, 0, len(s.items))
	for _, a := range s.items {
		items = append(items, a)
	}
	s.mu.RUnlock()

	if s.supervisor.Available() {
		for _, a := range items {
			s.syncStatus(a)
		}
	}

	sort.Slice(items, func(i, j int) bool { return items[i].Name < items[j].Name })
	return items
}

// Get возвращает приложение по id.
func (s *Store) Get(id string) (*App, error) {
	s.mu.RLock()
	app, ok := s.items[id]
	s.mu.RUnlock()
	if !ok {
		return nil, ErrNotFound
	}
	if s.supervisor.Available() {
		s.syncStatus(app)
	}
	return app, nil
}

func (s *Store) syncStatus(a *App) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	st, err := s.supervisor.Status(ctx, a.ID)
	if err != nil {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	a.Status = mapSupervisorStatus(st.Active)
	if !st.StartedAt.IsZero() {
		a.StartedAt = st.StartedAt
	}
	if st.MemoryMB > 0 {
		a.MemoryMB = st.MemoryMB
	}
}

func mapSupervisorStatus(s supervisor.Status) Status {
	switch s {
	case supervisor.StatusRunning:
		return StatusRunning
	case supervisor.StatusStopped:
		return StatusStopped
	case supervisor.StatusFailed:
		return StatusFailed
	default:
		return StatusUnknown
	}
}

// Restart перезапускает приложение.
func (s *Store) Restart(id string) error {
	s.mu.RLock()
	_, ok := s.items[id]
	s.mu.RUnlock()
	if !ok {
		return ErrNotFound
	}

	if s.supervisor.Available() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := s.supervisor.Restart(ctx, id); err != nil {
			return err
		}
	}

	s.mu.Lock()
	app := s.items[id]
	app.Status = StatusRunning
	app.StartedAt = time.Now().UTC()
	s.mu.Unlock()
	s.logger.Info("app restarted", "id", id)
	return nil
}

// Stop останавливает приложение.
func (s *Store) Stop(id string) error {
	s.mu.RLock()
	_, ok := s.items[id]
	s.mu.RUnlock()
	if !ok {
		return ErrNotFound
	}

	if s.supervisor.Available() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := s.supervisor.Stop(ctx, id); err != nil {
			return err
		}
	}

	s.mu.Lock()
	s.items[id].Status = StatusStopped
	s.mu.Unlock()
	s.logger.Info("app stopped", "id", id)
	return nil
}

// Start запускает приложение.
func (s *Store) Start(id string) error {
	s.mu.RLock()
	_, ok := s.items[id]
	s.mu.RUnlock()
	if !ok {
		return ErrNotFound
	}
	if s.supervisor.Available() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := s.supervisor.Start(ctx, id); err != nil {
			return err
		}
	}
	s.mu.Lock()
	app := s.items[id]
	app.Status = StatusRunning
	app.StartedAt = time.Now().UTC()
	s.mu.Unlock()
	s.logger.Info("app started", "id", id)
	return nil
}

// Install регистрирует новое приложение и устанавливает его как systemd unit.
func (s *Store) Install(spec supervisor.AppSpec, meta App) error {
	if s.supervisor.Available() {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		if err := s.supervisor.Install(ctx, spec); err != nil {
			return err
		}
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	meta.ID = spec.ID
	if meta.Name == "" {
		meta.Name = spec.Name
	}
	if meta.Status == "" {
		meta.Status = StatusStopped
	}
	meta.StartCmd = spec.StartCmd
	s.items[spec.ID] = &meta
	s.logger.Info("app installed", "id", spec.ID)
	return nil
}

// Uninstall удаляет приложение и его systemd unit.
func (s *Store) Uninstall(id string) error {
	s.mu.RLock()
	_, ok := s.items[id]
	s.mu.RUnlock()
	if !ok {
		return ErrNotFound
	}
	if s.supervisor.Available() {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		if err := s.supervisor.Uninstall(ctx, id); err != nil {
			return err
		}
	}
	s.mu.Lock()
	delete(s.items, id)
	s.mu.Unlock()
	s.logger.Info("app uninstalled", "id", id)
	return nil
}
