package supervisor

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"syscall"
	"time"
)

// ExecSupervisor — реализация Supervisor без systemd, через os/exec.
//
// Нужен для Android (Linux ядро без systemctl) и любых других Linux-окружений
// без systemd. Свойства:
//
//   • Install — просто создаёт WorkingDir, без unit-файлов
//   • Start — fork+exec /bin/sh -c <StartCmd> из WorkingDir, процесс в отдельной process group
//   • Stop — SIGTERM всей group, ждём 5s, потом SIGKILL
//   • Restart — Stop + Start
//   • Status — по map[id] и health проверки
//   • FollowLogs — stdout/stderr процесса стримятся в канал
//
// Состояние в памяти — при перезапуске агента все запущенные процессы
// объявляются Stopped. Persistence — отдельный этап.
type ExecSupervisor struct {
	logger  *slog.Logger
	appsDir string
	shell   string // "/bin/sh" или "/system/bin/sh" (Android)

	mu      sync.Mutex
	running map[string]*runningProc
}

type runningProc struct {
	cmd        *exec.Cmd
	cancel     context.CancelFunc
	startedAt  time.Time
	exitCode   int
	exitedAt   time.Time
	lastStatus Status
	logChans   []chan string
	logMu      sync.Mutex
}

// NewExecSupervisor создаёт реализацию без systemd.
//
// shell — абсолютный путь к sh. Пустой = автодетект (Android /system/bin/sh или /bin/sh).
func NewExecSupervisor(logger *slog.Logger, appsDir, shell string) *ExecSupervisor {
	if shell == "" {
		shell = detectShell()
	}
	return &ExecSupervisor{
		logger:  logger,
		appsDir: appsDir,
		shell:   shell,
		running: make(map[string]*runningProc),
	}
}

// detectShell — на Android sh лежит в /system/bin, на обычном Linux — в /bin.
func detectShell() string {
	for _, p := range []string{"/system/bin/sh", "/bin/sh", "/usr/bin/sh"} {
		if info, err := os.Stat(p); err == nil && !info.IsDir() {
			return p
		}
	}
	return "/bin/sh" // фолбэк, exec.Start вернёт понятную ошибку
}

func (s *ExecSupervisor) Available() bool { return true }

func (s *ExecSupervisor) Install(_ context.Context, spec AppSpec) error {
	if err := validateID(spec.ID); err != nil {
		return err
	}
	if spec.WorkingDir == "" {
		spec.WorkingDir = filepath.Join(s.appsDir, spec.ID)
	}
	if err := os.MkdirAll(spec.WorkingDir, 0o755); err != nil {
		return fmt.Errorf("create workdir: %w", err)
	}
	s.logger.Info("app installed (exec)", "id", spec.ID, "workdir", spec.WorkingDir)
	return nil
}

func (s *ExecSupervisor) Uninstall(ctx context.Context, id string) error {
	if err := validateID(id); err != nil {
		return err
	}
	_ = s.Stop(ctx, id)
	s.logger.Info("app uninstalled (exec)", "id", id)
	return nil
}

func (s *ExecSupervisor) Start(ctx context.Context, id string) error {
	return s.startWithSpec(ctx, AppSpec{ID: id, StartCmd: "", WorkingDir: filepath.Join(s.appsDir, id)})
}

// StartWithSpec — вариант для Store, где известна полная спеца. Supervisor interface не знает
// специфику — store передаёт её явно.
func (s *ExecSupervisor) StartWithSpec(ctx context.Context, spec AppSpec) error {
	return s.startWithSpec(ctx, spec)
}

func (s *ExecSupervisor) startWithSpec(_ context.Context, spec AppSpec) error {
	if err := validateID(spec.ID); err != nil {
		return err
	}
	s.mu.Lock()
	if rp, ok := s.running[spec.ID]; ok && rp.cmd != nil && rp.cmd.Process != nil && rp.lastStatus == StatusRunning {
		s.mu.Unlock()
		return nil // уже запущен — идемпотентно
	}
	s.mu.Unlock()

	workdir := spec.WorkingDir
	if workdir == "" {
		workdir = filepath.Join(s.appsDir, spec.ID)
	}
	if err := os.MkdirAll(workdir, 0o755); err != nil {
		return fmt.Errorf("workdir: %w", err)
	}
	if spec.StartCmd == "" {
		return errors.New("empty start_cmd")
	}

	// Отдельный контекст — cancel используется при Stop для SIGKILL.
	runCtx, cancel := context.WithCancel(context.Background())
	cmd := exec.CommandContext(runCtx, s.shell, "-c", spec.StartCmd)
	cmd.Dir = workdir
	cmd.Env = mergeEnv(spec.Env)
	// process group, чтобы SIGTERM убивал и потомков.
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		cancel()
		return fmt.Errorf("stdout pipe: %w", err)
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		cancel()
		return fmt.Errorf("stderr pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		cancel()
		return fmt.Errorf("start: %w", err)
	}

	rp := &runningProc{
		cmd:        cmd,
		cancel:     cancel,
		startedAt:  time.Now().UTC(),
		lastStatus: StatusRunning,
	}

	s.mu.Lock()
	s.running[spec.ID] = rp
	s.mu.Unlock()

	// Стримять stdout/stderr в каналы подписчиков.
	go s.pumpLogs(rp, stdout, "stdout")
	go s.pumpLogs(rp, stderr, "stderr")

	// Reaper.
	go func() {
		waitErr := cmd.Wait()
		s.mu.Lock()
		rp.exitedAt = time.Now().UTC()
		if waitErr != nil {
			var ee *exec.ExitError
			if errors.As(waitErr, &ee) {
				rp.exitCode = ee.ExitCode()
				rp.lastStatus = StatusFailed
			} else {
				rp.lastStatus = StatusFailed
			}
		} else {
			rp.lastStatus = StatusStopped
		}
		s.mu.Unlock()
		rp.closeLogs()
		s.logger.Info("exec app exited", "id", spec.ID, "code", rp.exitCode, "status", rp.lastStatus)
	}()

	s.logger.Info("exec app started", "id", spec.ID, "cmd", spec.StartCmd, "pid", cmd.Process.Pid)
	return nil
}

func (s *ExecSupervisor) Stop(_ context.Context, id string) error {
	if err := validateID(id); err != nil {
		return err
	}
	s.mu.Lock()
	rp, ok := s.running[id]
	s.mu.Unlock()
	if !ok || rp.cmd == nil || rp.cmd.Process == nil || rp.lastStatus != StatusRunning {
		return nil // не запущен — идемпотентно
	}

	pid := rp.cmd.Process.Pid
	// SIGTERM всей process group.
	if err := syscall.Kill(-pid, syscall.SIGTERM); err != nil {
		s.logger.Warn("kill -TERM", "id", id, "err", err)
	}

	// Ждём 5s graceful.
	done := make(chan struct{})
	go func() {
		for {
			s.mu.Lock()
			done1 := rp.lastStatus != StatusRunning
			s.mu.Unlock()
			if done1 {
				close(done)
				return
			}
			time.Sleep(100 * time.Millisecond)
		}
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		_ = syscall.Kill(-pid, syscall.SIGKILL)
		if rp.cancel != nil {
			rp.cancel()
		}
	}
	return nil
}

func (s *ExecSupervisor) Restart(ctx context.Context, id string) error {
	if err := s.Stop(ctx, id); err != nil {
		s.logger.Warn("restart: stop failed", "err", err)
	}
	return s.Start(ctx, id)
}

func (s *ExecSupervisor) Status(_ context.Context, id string) (UnitStatus, error) {
	if err := validateID(id); err != nil {
		return UnitStatus{Active: StatusUnknown}, err
	}
	s.mu.Lock()
	rp, ok := s.running[id]
	s.mu.Unlock()
	if !ok {
		return UnitStatus{Active: StatusStopped}, nil
	}
	return UnitStatus{
		Active:    rp.lastStatus,
		StartedAt: rp.startedAt,
		ExitCode:  rp.exitCode,
	}, nil
}

func (s *ExecSupervisor) FollowLogs(ctx context.Context, id string) (<-chan string, error) {
	if err := validateID(id); err != nil {
		return nil, err
	}
	s.mu.Lock()
	rp, ok := s.running[id]
	s.mu.Unlock()
	if !ok {
		return nil, fmt.Errorf("app %q not running", id)
	}
	ch := make(chan string, 64)
	rp.logMu.Lock()
	rp.logChans = append(rp.logChans, ch)
	rp.logMu.Unlock()

	// При отмене контекста — убираем подписчика.
	go func() {
		<-ctx.Done()
		rp.logMu.Lock()
		defer rp.logMu.Unlock()
		for i, c := range rp.logChans {
			if c == ch {
				rp.logChans = append(rp.logChans[:i], rp.logChans[i+1:]...)
				close(ch)
				return
			}
		}
	}()
	return ch, nil
}

// pumpLogs читает stdout/stderr процесса и рассылает по подписчикам.
func (s *ExecSupervisor) pumpLogs(rp *runningProc, r io.Reader, source string) {
	scanner := bufio.NewScanner(r)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		line := source + ": " + scanner.Text()
		rp.logMu.Lock()
		for _, c := range rp.logChans {
			select {
			case c <- line:
			default:
				// Медленный читатель — теряем строку.
			}
		}
		rp.logMu.Unlock()
	}
}

func (rp *runningProc) closeLogs() {
	rp.logMu.Lock()
	defer rp.logMu.Unlock()
	for _, c := range rp.logChans {
		close(c)
	}
	rp.logChans = nil
}

// mergeEnv добавляет spec.Env к текущему окружению родительского процесса,
// фильтруя небезопасные ключи.
func mergeEnv(custom map[string]string) []string {
	env := os.Environ()
	for k, v := range custom {
		if !isSafeEnvKey(k) {
			continue
		}
		env = append(env, k+"="+v)
	}
	return env
}
