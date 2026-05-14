// Package sshd — встроенный SSH-сервер внутрь VibeFly-agent.
//
// Почему не OpenSSH:
//   - Нет обязательных system users (на Android это проблема — один UID на весь sandbox)
//   - Нет внешнего бинаря в APK дополнительно (экономия размера)
//   - go-side controls: мы решаем куда ставить cwd, какие env выставлять
//
// Основа — gliderlabs/ssh (pure Go wrapper над golang.org/x/crypto/ssh). При подключении
// запускаем shell в cwd=apps_dir, env'ом выставляем VIBEFLY_APPS_DIR, VIBEFLY_LOGS_DIR.
// Пользователь видит папки своих приложений, может cd, ls, tail -f spec.json и т.д.
//
// PTY: поддерживается только на Linux (в т.ч. Android) через build-tagовый pty_linux.go.
// На Windows-девхосте pty_other.go не даёт запустить shell — но агент в sshd режиме там
// всё равно не запускается по умолчанию.
package sshd

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	gliderssh "github.com/gliderlabs/ssh"
	"golang.org/x/crypto/ssh"
)

// Options — параметры NewServer.
type Options struct {
	Logger       *slog.Logger
	Listen       string // 0.0.0.0:2222
	HostKeyPath  string // файл host-ключа ed25519; создаётся если отсутствует
	AuthKeysPath string // ~/.ssh/authorized_keys стиль (один ключ на строку)
	Password     string // fallback для первого старта; "" — отключён
	Shell        string // /system/bin/sh; "" — autodetect
	Workdir      string // где открывается shell (apps_dir)
	AppsDir      string // для env VIBEFLY_APPS_DIR
	LogsDir      string // для env VIBEFLY_LOGS_DIR
}

// Server — жизненный цикл SSH-демона. Start блокирующий; вызывай в go func().
type Server struct {
	log      *slog.Logger
	opts     Options
	mu       sync.Mutex
	inner    *gliderssh.Server
	listener net.Listener
}

func New(opts Options) *Server {
	if opts.Logger == nil {
		opts.Logger = slog.Default()
	}
	if opts.Shell == "" {
		opts.Shell = detectShell()
	}
	if opts.Workdir == "" {
		opts.Workdir = opts.AppsDir
	}
	return &Server{log: opts.Logger.With("component", "sshd"), opts: opts}
}

func detectShell() string {
	for _, p := range []string{"/system/bin/sh", "/bin/sh", "/usr/bin/sh"} {
		if info, err := os.Stat(p); err == nil && !info.IsDir() {
			return p
		}
	}
	return "/bin/sh"
}

// Start запускает сервер (блокирующий). Stop — graceful shutdown.
func (s *Server) Start() error {
	hostSigner, err := loadOrCreateHostKey(s.opts.HostKeyPath)
	if err != nil {
		return fmt.Errorf("host key: %w", err)
	}

	authKeysData, _ := os.ReadFile(s.opts.AuthKeysPath)
	authorized, err := parseAuthorizedKeys(authKeysData)
	if err != nil {
		s.log.Warn("не удалось прочитать authorized_keys", "path", s.opts.AuthKeysPath, "err", err)
	}

	srv := &gliderssh.Server{
		Addr:    s.opts.Listen,
		Handler: s.handleSession,
	}
	srv.AddHostKey(hostSigner)

	if len(authorized) > 0 {
		srv.PublicKeyHandler = func(_ gliderssh.Context, key gliderssh.PublicKey) bool {
			return keyInList(key, authorized)
		}
	}
	if s.opts.Password != "" {
		srv.PasswordHandler = func(_ gliderssh.Context, pwd string) bool {
			return subtleEq(pwd, s.opts.Password)
		}
	}
	if len(authorized) == 0 && s.opts.Password == "" {
		return errors.New("sshd: нет ни ключей в authorized_keys, ни password — вход запрещён")
	}

	ln, err := net.Listen("tcp", s.opts.Listen)
	if err != nil {
		return fmt.Errorf("listen %s: %w", s.opts.Listen, err)
	}
	s.mu.Lock()
	s.inner = srv
	s.listener = ln
	s.mu.Unlock()

	s.log.Info("sshd стартует", "listen", s.opts.Listen, "keys", len(authorized), "password", s.opts.Password != "")
	return srv.Serve(ln)
}

// Stop — грацфульный shutdown. Идемпотентен.
func (s *Server) Stop() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.inner == nil {
		return nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	err := s.inner.Shutdown(ctx)
	s.inner = nil
	s.listener = nil
	return err
}

// handleSession — вызывается при каждом подключении. Запускает shell в PTY.
func (s *Server) handleSession(sess gliderssh.Session) {
	ptyReq, winCh, isPty := sess.Pty()
	cmd := exec.Command(s.opts.Shell)
	cmd.Dir = s.opts.Workdir
	cmd.Env = sessionEnv(s.opts, ptyReq.Term)

	if !isPty {
		// Без PTY — режим exec/batch команд (ssh user@host "ls")
		if command := sess.Command(); len(command) > 0 {
			cmd = exec.Command(s.opts.Shell, "-c", strings.Join(command, " "))
			cmd.Dir = s.opts.Workdir
			cmd.Env = sessionEnv(s.opts, "")
		}
		cmd.Stdin = sess
		cmd.Stdout = sess
		cmd.Stderr = sess.Stderr()
		if err := cmd.Run(); err != nil {
			var ee *exec.ExitError
			if errors.As(err, &ee) {
				_ = sess.Exit(ee.ExitCode())
				return
			}
			fmt.Fprintln(sess.Stderr(), "vibefly-sshd:", err)
			_ = sess.Exit(1)
			return
		}
		_ = sess.Exit(0)
		return
	}

	// PTY-режим — реальный интерактивный shell.
	f, err := startPty(cmd)
	if err != nil {
		fmt.Fprintln(sess, "vibefly-sshd: PTY недоступен ("+err.Error()+")")
		_ = sess.Exit(1)
		return
	}
	defer f.Close()

	// resize handler
	go func() {
		for win := range winCh {
			_ = ptySetSize(f, win.Width, win.Height)
		}
	}()

	// bridge sess ↔ pty
	done := make(chan struct{}, 2)
	go func() { _, _ = io.Copy(f, sess); done <- struct{}{} }()
	go func() { _, _ = io.Copy(sess, f); done <- struct{}{} }()
	<-done

	if err := cmd.Wait(); err != nil {
		var ee *exec.ExitError
		if errors.As(err, &ee) {
			_ = sess.Exit(ee.ExitCode())
			return
		}
	}
	_ = sess.Exit(0)
}

func sessionEnv(o Options, term string) []string {
	env := os.Environ()
	env = append(env,
		"VIBEFLY_APPS_DIR="+o.AppsDir,
		"VIBEFLY_LOGS_DIR="+o.LogsDir,
		"VIBEFLY_SHELL=1",
		"PS1=vibefly:\\W$ ",
	)
	if term != "" {
		env = append(env, "TERM="+term)
	}
	return env
}

// loadOrCreateHostKey — читает ed25519 host key из path или генерирует при первом старте.
// Гарантирует что fingerprint переживает перезапуск (иначе ssh-client будет ругаться).
func loadOrCreateHostKey(path string) (gliderssh.Signer, error) {
	if path == "" {
		return nil, errors.New("empty host_key_path")
	}
	if data, err := os.ReadFile(path); err == nil {
		signer, perr := ssh.ParsePrivateKey(data)
		if perr != nil {
			return nil, fmt.Errorf("parse host key: %w", perr)
		}
		return signer, nil
	} else if !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	// Генерация
	_, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}
	// PKCS8 энкодинг через ssh.MarshalPrivateKey
	pem, err := ssh.MarshalPrivateKey(priv, "vibefly-agent-host-key")
	if err != nil {
		return nil, fmt.Errorf("marshal host key: %w", err)
	}
	if err := os.MkdirAll(filepathDir(path), 0o700); err != nil {
		return nil, err
	}
	if err := writePemFile(path, pem); err != nil {
		return nil, err
	}
	return ssh.NewSignerFromKey(priv)
}

func filepathDir(p string) string {
	return filepath.Dir(p)
}

func writePemFile(path string, block *pem.Block) error {
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()
	return pem.Encode(f, block)
}

// parseAuthorizedKeys — парсит OpenSSH authorized_keys (один ключ на строку).
func parseAuthorizedKeys(data []byte) ([]gliderssh.PublicKey, error) {
	if len(data) == 0 {
		return nil, nil
	}
	var out []gliderssh.PublicKey
	rest := data
	for len(rest) > 0 {
		key, _, _, next, err := ssh.ParseAuthorizedKey(rest)
		if err != nil {
			// Пропустим битую строку: найти \n и продолжить
			idx := strings.IndexByte(string(rest), '\n')
			if idx < 0 {
				break
			}
			rest = rest[idx+1:]
			continue
		}
		out = append(out, key)
		rest = next
	}
	return out, nil
}

func keyInList(needle gliderssh.PublicKey, list []gliderssh.PublicKey) bool {
	nm := needle.Marshal()
	for _, k := range list {
		if bytesEqual(k.Marshal(), nm) {
			return true
		}
	}
	return false
}

func bytesEqual(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	var v byte
	for i := range a {
		v |= a[i] ^ b[i]
	}
	return v == 0
}

func subtleEq(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	var v byte
	for i := 0; i < len(a); i++ {
		v |= a[i] ^ b[i]
	}
	return v == 0
}

// AppendAuthorizedKey — добавляет публичный ключ в authorized_keys (идемпотентно).
// Используется router'ом POST /ssh/keys.
func AppendAuthorizedKey(authKeysPath, openSshPublicKey string) error {
	trimmed := strings.TrimSpace(openSshPublicKey)
	if trimmed == "" {
		return errors.New("пустой ключ")
	}
	if _, _, _, _, err := ssh.ParseAuthorizedKey([]byte(trimmed)); err != nil {
		return fmt.Errorf("не похоже на OpenSSH ключ: %w", err)
	}
	if err := os.MkdirAll(filepath.Dir(authKeysPath), 0o700); err != nil {
		return err
	}
	existing, _ := os.ReadFile(authKeysPath)
	if strings.Contains(string(existing), trimmed) {
		return nil // уже есть
	}
	f, err := os.OpenFile(authKeysPath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()
	if len(existing) > 0 && existing[len(existing)-1] != '\n' {
		if _, err := f.WriteString("\n"); err != nil {
			return err
		}
	}
	_, err = f.WriteString(trimmed + "\n")
	return err
}

// CountAuthorizedKeys — сколько ключей сейчас в файле (для UI).
func CountAuthorizedKeys(authKeysPath string) int {
	data, err := os.ReadFile(authKeysPath)
	if err != nil {
		return 0
	}
	keys, _ := parseAuthorizedKeys(data)
	return len(keys)
}

// FingerprintHostKey — возвращает SHA256:... для показа в UI.
// Пользователь сравнивает с тем что ssh-client показывает при first connect.
func FingerprintHostKey(hostKeyPath string) string {
	data, err := os.ReadFile(hostKeyPath)
	if err != nil {
		return ""
	}
	signer, err := ssh.ParsePrivateKey(data)
	if err != nil {
		return ""
	}
	return ssh.FingerprintSHA256(signer.PublicKey())
}

// errnoIs — helper для windows-only fallback (не используется на Linux/Android но
// нужен чтобы cross-compile проходил).
func errnoIs(err error, target syscall.Errno) bool {
	var e syscall.Errno
	return errors.As(err, &e) && e == target
}
