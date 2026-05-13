//go:build linux

package supervisor

import (
	"os/exec"
	"syscall"
)

// setProcAttrIsolated — выделяет process group, чтобы SIGTERM/SIGKILL
// били сразу всех потомков процесса.
func setProcAttrIsolated(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
}

// terminateProcessGroup — SIGTERM всей process group.
func terminateProcessGroup(pid int) error {
	return syscall.Kill(-pid, syscall.SIGTERM)
}

// killProcessGroup — SIGKILL всей process group.
func killProcessGroup(pid int) error {
	return syscall.Kill(-pid, syscall.SIGKILL)
}
