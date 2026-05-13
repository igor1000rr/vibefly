// Package apps — управление пользовательскими приложениями.
//
// Store держит метаданные приложений в памяти и (если apps_dir задан)
// на диске в <apps_dir>/<id>/spec.json. Реальные операции (start/stop/restart, статус,
// логи) делегирует supervisor'у.
package apps

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"os"
	"path/filepath"
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

// persistedSpec — что записывается в <apps_dir>/<id>/spec.json.
// Содержит и metadata (для UI), и supervisor.AppSpec (для Start).
type persistedSpec struct {
	Meta App                 `json:"meta"`
	Spec supervisor.AppSpec  `json:"spec"`
	Env  map[string]string   `json:"env,omitempty"`
}

// ErrNotFound — приложение с таким id не найдено.
var ErrNotFound = errors.New("app not found")

// Store держит список приложений.
type Store struct {
	mu         sync.RWMutex
	items      map[string]*App
	specs      map[string]supervisor.AppSpec // кэш спецов для Start по ExecSupervisor
	logger     *slog.Logger
	supervisor supervisor.Supervisor
	appsDir    string // если пусто — persistence отключен (для тестов)
}

// NewStore создаёт хранилище.
//
//   - seedDemo только если supervisor недоступен (Windows-dev fake mode).
//   - Если supervisor.Available() и appsDir != "" — сканирует диск, восстанавливает
//     приложения. Статусы сбрасываются в Stopped — явный autostart в этой фазе не
//     делается, пользователь решает из UI что запускать.
func NewStore(logger *slog.Logger, sup supervisor.Supervisor, seedDemo bool) *Store {
	return NewStoreWithDir(logger, sup, "", seedDemo)
}

// NewStoreWithDir — вариант с persistence. appsDir — корневая директория для spec.json.
func NewStoreWithDir(logger *slog.Logger, sup supervisor.Supervisor, appsDir string, seedDemo bool) *Store {
	s := &Store{
		items:      make(map[string]*App),
		specs:      make(map[string]supervisor.AppSpec),
		logger:     logger,
		supervisor: sup,
		appsDir:    appsDir,
	}
	if sup.Available() {
		if appsDir != "" {
			s.loadFromDisk()
		}
	} else if seedDemo {
		s.seedFakes()
	}
	return s
}

// SupervisorAvailable — фасад для UI: показать ли реальное состояние или demo-mode.
func (s *Store) SupervisorAvailable() bool { return s.supervisor.Available() }

// loadFromDisk сканирует appsDir, ищет spec.json в каждой подпапке
// и восстанавливает metadata + spec. Статусы принудительно Stopped.
func (s *Store) loadFromDisk() {
	entries, err := os.ReadDir(s.appsDir)
	if err != nil {
		if !errors.Is(err, os.ErrNotExist) {
			s.logger.Warn("не удалось открыть apps_dir", "path", s.appsDir, "err", err)
		}
		return
	}
	restored := 0
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		specPath := filepath.Join(s.appsDir, e.Name(), "spec.json")
		data, err := os.ReadFile(specPath)
		if err != nil {
			continue
		}
		var ps persistedSpec
		if err := json.Unmarshal(data, &ps); err != nil {
			s.logger.Warn("битый spec.json", "id", e.Name(), "err", err)
			continue
		}
		if ps.Meta.ID == "" {
			ps.Meta.ID = e.Name()
		}
		ps.Meta.Status = StatusStopped // после перезапуска все приложения остановлены
		if ps.Spec.Env == nil {
			ps.Spec.Env = ps.Env
		}
		s.items[ps.Meta.ID] = &ps.Meta
		s.specs[ps.Meta.ID] = ps.Spec
		restored++
	}
	if restored > 0 {
		s.logger.Info("восстановлены приложения", "count", restored, "dir", s.appsDir)
	}
}

// persistSpec пишет spec.json для приложения.
func (s *Store) persistSpec(meta App, spec supervisor.AppSpec) {
	if s.appsDir == "" {
		return
	}
	dir := filepath.Join(s.appsDir, meta.ID)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		s.logger.Warn("persist: mkdir", "id", meta.ID, "err", err)
		return
	}
	ps := persistedSpec{Meta: meta, Spec: spec, Env: spec.Env}
	data, err := json.MarshalIndent(ps, "", "  ")
	if err != nil {
		s.logger.Warn("persist: marshal", "id", meta.ID, "err", err)
		return
	}
	specPath := filepath.Join(dir, "spec.json")
	if err := os.WriteFile(specPath, data, 0o644); err != nil {
		s.logger.Warn("persist: write", "id", meta.ID, "err", err)
	}
}

func (s *Store) deletePersisted(id string) {
	if s.appsDir == "" {
		return
	}
	specPath := filepath.Join(s.appsDir, id, "spec.json")
	if err := os.Remove(specPath); err != nil && !errors.Is(err, os.ErrNotExist) {
		s.logger.Warn("не удалось удалить spec.json", "id", id, "err", err)
	}
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
		if err := s.startWithSpecOrFallback(ctx, id, true); err != nil {
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
		if err := s.startWithSpecOrFallback(ctx, id, false); err != nil {
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

// startWithSpecOrFallback — если supervisor умеет StartSpec (ExecSupervisor),
// передаём полный spec из кэша. Иначе — обычный Start(id) (SystemdSupervisor
// подымет unit с диска).
func (s *Store) startWithSpecOrFallback(ctx context.Context, id string, isRestart bool) error {
	s.mu.RLock()
	spec, hasSpec := s.specs[id]
	s.mu.RUnlock()

	if ss, ok := s.supervisor.(supervisor.SpecStarter); ok && hasSpec {
		if isRestart {
			_ = s.supervisor.Stop(ctx, id)
		}
		return ss.StartSpec(ctx, spec)
	}
	if isRestart {
		return s.supervisor.Restart(ctx, id)
	}
	return s.supervisor.Start(ctx, id)
}

func (s *Store) Install(spec supervisor.AppSpec, meta App) error {
	if s.supervisor.Available() {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		if err := s.supervisor.Install(ctx, spec); err != nil {
			return err
		}
	}
	s.mu.Lock()
	meta.ID = spec.ID
	if meta.Name == "" {
		meta.Name = spec.Name
	}
	if meta.Status == "" {
		meta.Status = StatusStopped
	}
	meta.StartCmd = spec.StartCmd
	s.items[spec.ID] = &meta
	s.specs[spec.ID] = spec
	s.mu.Unlock()
	s.persistSpec(meta, spec)
	s.logger.Info("app installed", "id", spec.ID)
	return nil
}

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
	delete(s.specs, id)
	s.mu.Unlock()
	s.deletePersisted(id)
	s.logger.Info("app uninstalled", "id", id)
	return nil
}
