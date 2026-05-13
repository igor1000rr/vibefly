//go:build !linux

package supervisor

import (
	"os/exec"
)

// setProcAttrIsolated — no-op на non-Linux (Windows/macOS dev-host).
// ExecSupervisor всё равно не используется вне Linux (фабрика выбирает Nop),
// но тип компилится ради без build-ошибок.
func setProcAttrIsolated(_ *exec.Cmd) {
	// noop
}

func terminateProcessGroup(_ int) error {
	return nil
}

func killProcessGroup(_ int) error {
	return nil
}
