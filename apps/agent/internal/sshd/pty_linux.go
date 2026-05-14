//go:build linux

package sshd

import (
	"os"
	"os/exec"
	"syscall"
	"unsafe"
)

// PTY для Linux/Android. Берём /dev/ptmx, grant, unlock, открываем slave в дочке.
// Избегаем внешних зависимостей вроде creack/pty — всего ~50 строк syscall.

func startPty(cmd *exec.Cmd) (*os.File, error) {
	master, err := os.OpenFile("/dev/ptmx", os.O_RDWR, 0)
	if err != nil {
		return nil, err
	}
	if err := ptyGrantUnlock(master); err != nil {
		master.Close()
		return nil, err
	}
	slaveName, err := ptySlaveName(master)
	if err != nil {
		master.Close()
		return nil, err
	}
	slave, err := os.OpenFile(slaveName, os.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		master.Close()
		return nil, err
	}

	cmd.Stdin = slave
	cmd.Stdout = slave
	cmd.Stderr = slave
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setsid:  true,
		Setctty: true,
	}

	if err := cmd.Start(); err != nil {
		master.Close()
		slave.Close()
		return nil, err
	}
	_ = slave.Close() // в процессе родителя не нужен
	return master, nil
}

func ptyGrantUnlock(master *os.File) error {
	var n uintptr = 0
	if _, _, errno := syscall.Syscall(syscall.SYS_IOCTL, master.Fd(), syscall.TIOCSPTLCK, uintptr(unsafe.Pointer(&n))); errno != 0 {
		return errno
	}
	return nil
}

func ptySlaveName(master *os.File) (string, error) {
	var n uint32
	if _, _, errno := syscall.Syscall(syscall.SYS_IOCTL, master.Fd(), syscall.TIOCGPTN, uintptr(unsafe.Pointer(&n))); errno != 0 {
		return "", errno
	}
	return "/dev/pts/" + uitoa(uint(n)), nil
}

func uitoa(v uint) string {
	if v == 0 {
		return "0"
	}
	var buf [10]byte
	i := len(buf)
	for v > 0 {
		i--
		buf[i] = byte('0' + v%10)
		v /= 10
	}
	return string(buf[i:])
}

// winSize в формате равном к struct winsize из termios.
type winSize struct {
	rows, cols uint16
	x, y       uint16
}

func ptySetSize(f *os.File, w, h int) error {
	ws := winSize{rows: uint16(h), cols: uint16(w)}
	_, _, errno := syscall.Syscall(syscall.SYS_IOCTL, f.Fd(), syscall.TIOCSWINSZ, uintptr(unsafe.Pointer(&ws)))
	if errno != 0 {
		return errno
	}
	return nil
}
