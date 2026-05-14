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
	"time"
)

// ExecSupervisor — реализация Supervisor без systemd, через os/exec.
//
// Platform-specific syscalls (Setpgid, kill -group) живут в exec_linux.go /
// exec_other.go — в файлах с build tag'ами.
//
// Restart policy:
//   - "" или "no"     — никогда не рестартить (по умолчанию)
//   - "on-failure"     — рестарт только если exit code ≠ 0
//   - "always"         — рестарт после любого exit'а (включая нормальный)
//
// Защита от бесконечных crash-лупов: если процесс прожил меньше
// MinUptimeForRestart (5s) до exit'а — рестарт НЕ делается, статус Failed.
// Это предотвращает "./broken.sh" от бесконечного цикла fork → exit.
type ExecSupervisor struct {
	logger  *slog.Logger
	appsDir string
	shell   string
	logSink LogSink

	mu      sync.Mutex
	running map[string]*runningProc
}

// MinUptimeForRestart — если процесс прожил меньше этого времени, считаем
// что он "broken" и не рестартим. Обычный service должен доживать до сих пор
// без проблем.
const MinUptimeForRestart = 5 * time.Second

// RestartDelay — задержка перед auto-restart после exit'а. Позволяет
// системным ресурсам (порт освободиться, файл lock release) очиститься.
const RestartDelay = 3 * time.Second

type runningProc struct {
	cmd        *exec.Cmd
	spec       AppSpec // нужен для restart policy — reaper goroutine перезапускает по спеку
	startedAt  time.Time
	exitCode   int
	exitedAt   time.Time
	lastStatus Status
	manualStop bool // выставляется в Stop() — reaper пропускает рестарт
	logChans   []chan string
	logMu      sync.Mutex
}

func NewExecSupervisor(logger *slog.Logger, appsDir, shell string, logSink LogSink) *ExecSupervisor {
	if shell == "" {
		shell = detectShell()
	}
	return &ExecSupervisor{
		logger:  logger,
		appsDir: appsDir,
		shell:   shell,
		logSink: logSink,
		running: make(map[string]*runningProc),
	}
}

func detectShell() string {
	for _, p := range []string{"/system/bin/sh", "/bin/sh", "/usr/bin/sh"} {
		if info, err := os.Stat(p); err == nil && !info.IsDir() {
			return p
		}
	}
	return "/bin/sh"
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
	return s.startWithSpec(ctx, AppSpec{ID: id, WorkingDir: filepath.Join(s.appsDir, id)})
}

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
		return nil
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

	cmd := exec.Command(s.shell, "-c", spec.StartCmd)
	cmd.Dir = workdir
	cmd.Env = mergeEnv(spec.Env)
	setProcAttrIsolated(cmd)

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return fmt.Errorf("stdout pipe: %w", err)
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return fmt.Errorf("stderr pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("start: %w", err)
	}

	rp := &runningProc{
		cmd:        cmd,
		spec:       spec,
		startedAt:  time.Now().UTC(),
		lastStatus: StatusRunning,
	}

	s.mu.Lock()
	s.running[spec.ID] = rp
	s.mu.Unlock()

	go s.pumpLogs(spec.ID, rp, stdout, "stdout")
	go s.pumpLogs(spec.ID, rp, stderr, "stderr")

	go s.reap(spec.ID, rp, cmd)

	s.logger.Info("exec app started", "id", spec.ID, "cmd", spec.StartCmd, "pid", cmd.Process.Pid, "restart_policy", spec.RestartPolicy)
	return nil
}

// reap — вайтит процесс, фиксирует статус, решает перезапускать ли по политике.
func (s *ExecSupervisor) reap(id string, rp *runningProc, cmd *exec.Cmd) {
	waitErr := cmd.Wait()

	s.mu.Lock()
	rp.exitedAt = time.Now().UTC()
	if waitErr != nil {
		var ee *exec.ExitError
		if errors.As(waitErr, &ee) {
			rp.exitCode = ee.ExitCode()
		}
		rp.lastStatus = StatusFailed
	} else {
		rp.lastStatus = StatusStopped
	}
	manualStop := rp.manualStop
	spec := rp.spec
	exitCode := rp.exitCode
	lastStatus := rp.lastStatus
	uptime := rp.exitedAt.Sub(rp.startedAt)
	s.mu.Unlock()

	rp.closeLogs()
	s.logger.Info("exec app exited", "id", id, "code", exitCode, "status", lastStatus, "uptime_ms", uptime.Milliseconds())

	if manualStop {
		return
	}
	if !shouldRestart(spec.RestartPolicy, lastStatus) {
		return
	}
	if uptime < MinUptimeForRestart {
		s.logger.Warn("пропуск auto-restart: процесс прожил слишком мало (broken script?)",
			"id", id, "uptime", uptime, "min", MinUptimeForRestart)
		return
	}

	s.logger.Info("auto-restart запланирован", "id", id, "delay", RestartDelay, "policy", spec.RestartPolicy)
	time.Sleep(RestartDelay)

	// Перед рестартом проверяем что этот же rp всё ещё в map — иначе юзер уже
	// вызвал Uninstall/Start и мы не должны интерферировать.
	s.mu.Lock()
	current, exists := s.running[id]
	s.mu.Unlock()
	if !exists || current != rp {
		s.logger.Info("auto-restart отменён — состояние изменилось", "id", id)
		return
	}
	if err := s.startWithSpec(context.Background(), spec); err != nil {
		s.logger.Error("auto-restart не удался", "id", id, "err", err)
	}
}

// shouldRestart — решает нужен ли рестарт по политике и финальному статусу.
func shouldRestart(policy string, status Status) bool {
	switch policy {
	case "always":
		return true
	case "on-failure":
		return status == StatusFailed
	default:
		return false
	}
}

func (s *ExecSupervisor) Stop(_ context.Context, id string) error {
	if err := validateID(id); err != nil {
		return err
	}
	s.mu.Lock()
	rp, ok := s.running[id]
	if ok && rp != nil {
		rp.manualStop = true // reaper goroutine увидит флаг и не рестартнет
	}
	s.mu.Unlock()
	if !ok || rp.cmd == nil || rp.cmd.Process == nil || rp.lastStatus != StatusRunning {
		return nil
	}

	pid := rp.cmd.Process.Pid
	if err := terminateProcessGroup(pid); err != nil {
		s.logger.Warn("terminate failed", "id", id, "err", err)
	}

	done := make(chan struct{})
	go func() {
		for {
			s.mu.Lock()
			doneFlag := rp.lastStatus != StatusRunning
			s.mu.Unlock()
			if doneFlag {
				close(done)
				return
			}
			time.Sleep(100 * time.Millisecond)
		}
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		_ = killProcessGroup(pid)
		if rp.cmd.Process != nil {
			_ = rp.cmd.Process.Kill()
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

func (s *ExecSupervisor) pumpLogs(appID string, rp *runningProc, r io.Reader, source string) {
	scanner := bufio.NewScanner(r)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		raw := scanner.Text()
		line := source + ": " + raw

		rp.logMu.Lock()
		for _, c := range rp.logChans {
			select {
			case c <- line:
			default:
			}
		}
		rp.logMu.Unlock()

		if s.logSink != nil {
			s.logSink.AppendLine(appID, source, raw)
		}
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
