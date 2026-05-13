// Package supervisor — реальный менеджер пользовательских приложений.
//
// Иерархия реализаций:
//   - SystemdSupervisor — полный systemd, generated unit files, journalctl. Linux + systemctl.
//   - ExecSupervisor    — полноценный менеджер процессов без systemd. Linux без systemctl (Android).
//   - NopSupervisor    — заглушка для non-linux (Windows/macOS dev).
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
	"runtime"
	"strconv"
	"strings"
	"time"
)

// AppSpec — так пользователь описывает приложение при создании.
type AppSpec struct {
	ID         string
	Name       string
	WorkingDir string
	StartCmd   string
	Env        map[string]string
	MemoryMax  string
	CPUQuota   string
}

type Status string

const (
	StatusRunning Status = "running"
	StatusStopped Status = "stopped"
	StatusFailed  Status = "failed"
	StatusUnknown Status = "unknown"
)

type UnitStatus struct {
	Active    Status
	StartedAt time.Time
	MemoryMB  int
	CPUSec    float64
	ExitCode  int
}

type Supervisor interface {
	Available() bool
	Install(ctx context.Context, spec AppSpec) error
	Uninstall(ctx context.Context, id string) error
	Start(ctx context.Context, id string) error
	Stop(ctx context.Context, id string) error
	Restart(ctx context.Context, id string) error
	Status(ctx context.Context, id string) (UnitStatus, error)
	FollowLogs(ctx context.Context, id string) (<-chan string, error)
}

// New — фабрика. Выбирает реализацию по среде:
//   - non-linux        → NopSupervisor (либо без ExecSupervisor: fork/exec не syscall-clean)
//   - linux + systemd  → SystemdSupervisor (production VPS)
//   - linux + no sd    → ExecSupervisor (Android, минимальные Linux образы)
func New(logger *slog.Logger, unitDir, appsDir string) Supervisor {
	if runtime.GOOS != "linux" {
		return &NopSupervisor{logger: logger, reason: "non-linux host"}
	}
	if _, err := exec.LookPath("systemctl"); err != nil {
		logger.Info("supervisor: systemctl отсутствует, используем ExecSupervisor (Android-mode)")
		return NewExecSupervisor(logger, appsDir, "")
	}
	return &SystemdSupervisor{
		logger:  logger,
		unitDir: unitDir,
		appsDir: appsDir,
	}
}

// NopSupervisor — заглушка для non-linux (Windows dev-host, macOS dev-host).
type NopSupervisor struct {
	logger *slog.Logger
	reason string
}

func (n *NopSupervisor) Available() bool { return false }
func (n *NopSupervisor) Reason() string  { return n.reason }

func (n *NopSupervisor) Install(_ context.Context, _ AppSpec) error {
	return fmt.Errorf("supervisor unavailable: %s", n.reason)
}
func (n *NopSupervisor) Uninstall(_ context.Context, _ string) error {
	return fmt.Errorf("supervisor unavailable: %s", n.reason)
}
func (n *NopSupervisor) Start(_ context.Context, _ string) error {
	return fmt.Errorf("supervisor unavailable: %s", n.reason)
}
func (n *NopSupervisor) Stop(_ context.Context, _ string) error {
	return fmt.Errorf("supervisor unavailable: %s", n.reason)
}
func (n *NopSupervisor) Restart(_ context.Context, _ string) error {
	return fmt.Errorf("supervisor unavailable: %s", n.reason)
}
func (n *NopSupervisor) Status(_ context.Context, _ string) (UnitStatus, error) {
	return UnitStatus{Active: StatusUnknown}, fmt.Errorf("supervisor unavailable: %s", n.reason)
}
func (n *NopSupervisor) FollowLogs(_ context.Context, _ string) (<-chan string, error) {
	return nil, fmt.Errorf("supervisor unavailable: %s", n.reason)
}

// SystemdSupervisor — реальная реализация.
type SystemdSupervisor struct {
	logger  *slog.Logger
	unitDir string
	appsDir string
}

func (s *SystemdSupervisor) Available() bool { return true }

func unitName(id string) string { return "vibefly-app-" + id + ".service" }

func (s *SystemdSupervisor) Install(ctx context.Context, spec AppSpec) error {
	if err := validateID(spec.ID); err != nil {
		return err
	}
	if spec.WorkingDir == "" {
		spec.WorkingDir = filepath.Join(s.appsDir, spec.ID)
	}
	if err := os.MkdirAll(spec.WorkingDir, 0o755); err != nil {
		return fmt.Errorf("create workdir: %w", err)
	}
	unitPath := filepath.Join(s.unitDir, unitName(spec.ID))
	content := renderUnit(spec)
	if err := os.WriteFile(unitPath, []byte(content), 0o644); err != nil {
		return fmt.Errorf("write unit: %w", err)
	}
	if _, err := s.run(ctx, "systemctl", "daemon-reload"); err != nil {
		return err
	}
	if _, err := s.run(ctx, "systemctl", "enable", unitName(spec.ID)); err != nil {
		return err
	}
	s.logger.Info("app installed", "id", spec.ID, "unit", unitPath)
	return nil
}

func (s *SystemdSupervisor) Uninstall(ctx context.Context, id string) error {
	if err := validateID(id); err != nil {
		return err
	}
	_, _ = s.run(ctx, "systemctl", "stop", unitName(id))
	_, _ = s.run(ctx, "systemctl", "disable", unitName(id))
	unitPath := filepath.Join(s.unitDir, unitName(id))
	if err := os.Remove(unitPath); err != nil && !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("remove unit: %w", err)
	}
	_, _ = s.run(ctx, "systemctl", "daemon-reload")
	s.logger.Info("app uninstalled", "id", id)
	return nil
}

func (s *SystemdSupervisor) Start(ctx context.Context, id string) error {
	return s.simpleAction(ctx, "start", id)
}
func (s *SystemdSupervisor) Stop(ctx context.Context, id string) error {
	return s.simpleAction(ctx, "stop", id)
}
func (s *SystemdSupervisor) Restart(ctx context.Context, id string) error {
	return s.simpleAction(ctx, "restart", id)
}

func (s *SystemdSupervisor) simpleAction(ctx context.Context, action, id string) error {
	if err := validateID(id); err != nil {
		return err
	}
	_, err := s.run(ctx, "systemctl", action, unitName(id))
	return err
}

func (s *SystemdSupervisor) Status(ctx context.Context, id string) (UnitStatus, error) {
	if err := validateID(id); err != nil {
		return UnitStatus{Active: StatusUnknown}, err
	}
	out, err := s.run(ctx, "systemctl", "show", unitName(id),
		"--property=ActiveState,SubState,ExecMainStartTimestamp,MemoryCurrent,CPUUsageNSec,ExecMainStatus")
	if err != nil {
		return UnitStatus{Active: StatusUnknown}, err
	}
	return parseShow(out), nil
}

func (s *SystemdSupervisor) FollowLogs(ctx context.Context, id string) (<-chan string, error) {
	if err := validateID(id); err != nil {
		return nil, err
	}
	cmd := exec.CommandContext(ctx, "journalctl", "-fu", unitName(id), "--output=cat", "--no-pager")
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, fmt.Errorf("stdout pipe: %w", err)
	}
	cmd.Stderr = io.Discard
	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("journalctl start: %w", err)
	}

	ch := make(chan string, 32)
	go func() {
		defer close(ch)
		defer cmd.Wait()
		scanner := bufio.NewScanner(stdout)
		scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
		for scanner.Scan() {
			select {
			case <-ctx.Done():
				return
			case ch <- scanner.Text():
			}
		}
	}()
	return ch, nil
}

func (s *SystemdSupervisor) run(ctx context.Context, name string, args ...string) (string, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return string(out), fmt.Errorf("%s %v: %w (%s)", name, args, err, strings.TrimSpace(string(out)))
	}
	return string(out), nil
}

func validateID(id string) error {
	if id == "" {
		return errors.New("empty app id")
	}
	if len(id) > 64 {
		return errors.New("app id too long")
	}
	for _, r := range id {
		switch {
		case r >= 'a' && r <= 'z':
		case r >= 'A' && r <= 'Z':
		case r >= '0' && r <= '9':
		case r == '-' || r == '_':
		default:
			return fmt.Errorf("invalid char in app id: %q", r)
		}
	}
	return nil
}

func parseShow(out string) UnitStatus {
	st := UnitStatus{Active: StatusUnknown}
	for _, line := range strings.Split(out, "\n") {
		k, v, ok := strings.Cut(strings.TrimSpace(line), "=")
		if !ok {
			continue
		}
		switch k {
		case "ActiveState":
			switch v {
			case "active":
				st.Active = StatusRunning
			case "inactive":
				st.Active = StatusStopped
			case "failed":
				st.Active = StatusFailed
			}
		case "ExecMainStartTimestamp":
			if t, err := time.Parse("Mon 2006-01-02 15:04:05 MST", v); err == nil {
				st.StartedAt = t
			}
		case "MemoryCurrent":
			if n, err := strconv.ParseInt(v, 10, 64); err == nil && n > 0 {
				st.MemoryMB = int(n / (1024 * 1024))
			}
		case "CPUUsageNSec":
			if n, err := strconv.ParseInt(v, 10, 64); err == nil && n > 0 {
				st.CPUSec = float64(n) / 1e9
			}
		case "ExecMainStatus":
			if n, err := strconv.Atoi(v); err == nil {
				st.ExitCode = n
			}
		}
	}
	return st
}

func renderUnit(spec AppSpec) string {
	var b strings.Builder
	fmt.Fprintf(&b, "[Unit]\n")
	fmt.Fprintf(&b, "Description=VibeFly app %s\n", spec.Name)
	fmt.Fprintf(&b, "After=network.target\n\n")

	fmt.Fprintf(&b, "[Service]\n")
	fmt.Fprintf(&b, "Type=simple\n")
	fmt.Fprintf(&b, "WorkingDirectory=%s\n", spec.WorkingDir)
	fmt.Fprintf(&b, "ExecStart=/bin/sh -lc %q\n", spec.StartCmd)
	fmt.Fprintf(&b, "Restart=on-failure\n")
	fmt.Fprintf(&b, "RestartSec=5\n\n")

	if spec.MemoryMax != "" {
		fmt.Fprintf(&b, "MemoryMax=%s\n", spec.MemoryMax)
	}
	if spec.CPUQuota != "" {
		fmt.Fprintf(&b, "CPUQuota=%s\n", spec.CPUQuota)
	}

	fmt.Fprintf(&b, "PrivateTmp=yes\n")
	fmt.Fprintf(&b, "NoNewPrivileges=yes\n")
	fmt.Fprintf(&b, "ProtectSystem=full\n")
	fmt.Fprintf(&b, "ProtectHome=yes\n")
	fmt.Fprintf(&b, "ReadWritePaths=%s\n", spec.WorkingDir)

	for k, v := range spec.Env {
		if !isSafeEnvKey(k) {
			continue
		}
		fmt.Fprintf(&b, "Environment=%s=%s\n", k, escapeEnvValue(v))
	}

	fmt.Fprintf(&b, "\nStandardOutput=journal\n")
	fmt.Fprintf(&b, "StandardError=journal\n")
	fmt.Fprintf(&b, "SyslogIdentifier=vibefly-app-%s\n", spec.ID)

	fmt.Fprintf(&b, "\n[Install]\nWantedBy=multi-user.target\n")
	return b.String()
}

func isSafeEnvKey(k string) bool {
	if k == "" {
		return false
	}
	for _, r := range k {
		switch {
		case r >= 'A' && r <= 'Z':
		case r >= 'a' && r <= 'z':
		case r >= '0' && r <= '9':
		case r == '_':
		default:
			return false
		}
	}
	return true
}

func escapeEnvValue(v string) string {
	cleaned := strings.ReplaceAll(v, `"`, ``)
	return `"` + cleaned + `"`
}
