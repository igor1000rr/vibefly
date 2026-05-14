//go:build !linux

package sshd

import (
	"errors"
	"os"
	"os/exec"
)

// PTY на non-Linux (Windows/macOS dev) не поддерживается.
// Агент в проде живёт на Android (Linux без systemctl), там путь через pty_linux.go.

func startPty(_ *exec.Cmd) (*os.File, error) {
	return nil, errors.New("PTY доступен только на Linux")
}

func ptySetSize(_ *os.File, _, _ int) error {
	return errors.New("PTY доступен только на Linux")
}
