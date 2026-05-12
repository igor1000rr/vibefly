package tunnel

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"os/exec"
	"regexp"
	"sync"
	"syscall"
	"time"
)

// trycloudflareURLRe — паттерн который cloudflared печатает в stderr
// при запуске quick tunnel: "https://<random-words>.trycloudflare.com".
var trycloudflareURLRe = regexp.MustCompile(`https://[a-z0-9-]+\.trycloudflare\.com`)

// Cloudflared — Manager на базе бинаря cloudflared.
//
// Использует quick tunnel: cloudflared tunnel --url http://127.0.0.1:3001.
// В этом режиме каждый запуск получает новый случайный поддомен — это нормально
// для dev/dogfood, для production-домена нужен named tunnel с auth (фаза 3+).
type Cloudflared struct {
	logger         *slog.Logger
	binary         string
	targetURL      string
	startupTimeout time.Duration

	mu       sync.Mutex
	status   Status
	starting bool
	cmd      *exec.Cmd
	cancel   context.CancelFunc

	subsMu sync.Mutex
	subs   []chan Event
}

// CloudflaredOptions — параметры NewCloudflared.
type CloudflaredOptions struct {
	Logger         *slog.Logger
	Binary         string        // путь к бинарю; пустое = "cloudflared" в $PATH
	TargetURL      string        // куда проксировать, обычно http://127.0.0.1:3001
	StartupTimeout time.Duration // сколько ждать URL в stderr; default 60s
}

// NewCloudflared создаёт менеджер. Сам бинарь не запускается до Start().
func NewCloudflared(opts CloudflaredOptions) *Cloudflared {
	if opts.Logger == nil {
		opts.Logger = slog.Default()
	}
	if opts.StartupTimeout <= 0 {
		opts.StartupTimeout = 60 * time.Second
	}
	if opts.Binary == "" {
		opts.Binary = "cloudflared"
	}
	return &Cloudflared{
		logger:         opts.Logger,
		binary:         opts.Binary,
		targetURL:      opts.TargetURL,
		startupTimeout: opts.StartupTimeout,
		status:         Status{Provider: ProviderTryCloudflare},
	}
}

// Start запускает cloudflared и ждёт публичного URL в stderr.
// Возвращает Status с PublicURL когда туннель готов, либо ошибку.
func (c *Cloudflared) Start(ctx context.Context) (Status, error) {
	c.mu.Lock()
	if c.status.Active {
		s := c.status
		c.mu.Unlock()
		return s, ErrAlreadyActive
	}
	if c.starting {
		s := c.status
		c.mu.Unlock()
		return s, errors.New("tunnel: already starting")
	}
	c.starting = true
	c.mu.Unlock()

	defer func() {
		c.mu.Lock()
		c.starting = false
		c.mu.Unlock()
	}()

	runCtx, cancel := context.WithCancel(context.Background())
	cmd := exec.CommandContext(runCtx, c.binary,
		"tunnel",
		"--url", c.targetURL,
		"--no-autoupdate",
	)
	stderr, err := cmd.StderrPipe()
	if err != nil {
		cancel()
		return c.failWith(err.Error()), err
	}
	if err := cmd.Start(); err != nil {
		cancel()
		return c.failWith(err.Error()), err
	}

	c.mu.Lock()
	c.cmd = cmd
	c.cancel = cancel
	c.mu.Unlock()

	urlCh := make(chan string, 1)
	go c.scanForURL(stderr, urlCh)

	// Reaper: следим за выходом cloudflared. Если умер — обновим статус.
	go func() {
		waitErr := cmd.Wait()
		c.handleExit(waitErr)
	}()

	select {
	case url := <-urlCh:
		now := time.Now().UTC()
		c.mu.Lock()
		c.status = Status{
			Active:    true,
			PublicURL: url,
			StartedAt: &now,
			Provider:  ProviderTryCloudflare,
		}
		s := c.status
		c.mu.Unlock()
		c.broadcast(s)
		c.logger.Info("tunnel up", "url", url)
		return s, nil

	case <-ctx.Done():
		c.killWith("startup cancelled")
		return c.Status(), ctx.Err()

	case <-time.After(c.startupTimeout):
		c.killWith("startup timeout")
		return c.Status(), fmt.Errorf("cloudflared startup timeout (%s)", c.startupTimeout)
	}
}

// scanForURL читает stderr cloudflared. Когда находит trycloudflare URL —
// отправляет в urlCh (один раз) и продолжает дренировать поток, чтобы
// процесс не блокировался на полном pipe buffer.
func (c *Cloudflared) scanForURL(stderr io.Reader, urlCh chan<- string) {
	scanner := bufio.NewScanner(stderr)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	found := false
	for scanner.Scan() {
		line := scanner.Text()
		if !found {
			if m := trycloudflareURLRe.FindString(line); m != "" {
				select {
				case urlCh <- m:
				default:
				}
				found = true
			}
		}
		c.logger.Debug("cloudflared", "line", line)
	}
}

// handleExit вызывается reaper-горутиной когда cloudflared умирает.
func (c *Cloudflared) handleExit(err error) {
	c.mu.Lock()
	wasActive := c.status.Active
	msg := ""
	if err != nil {
		msg = err.Error()
		var ee *exec.ExitError
		if errors.As(err, &ee) {
			msg = fmt.Sprintf("exit %d: %s", ee.ExitCode(), ee.Error())
		}
	}
	c.status = Status{
		Provider:  ProviderTryCloudflare,
		Active:    false,
		LastError: msg,
	}
	s := c.status
	c.mu.Unlock()
	if wasActive {
		c.logger.Warn("tunnel down", "err", msg)
		c.broadcast(s)
	}
}

// Stop посылает SIGTERM и даёт 5 секунд на graceful shutdown,
// затем SIGKILL через cancel.
func (c *Cloudflared) Stop() error {
	c.mu.Lock()
	active := c.status.Active
	cmd := c.cmd
	cancel := c.cancel
	c.mu.Unlock()

	if !active || cmd == nil || cmd.Process == nil {
		return nil
	}

	_ = cmd.Process.Signal(syscall.SIGTERM)

	done := make(chan struct{})
	go func() {
		_, _ = cmd.Process.Wait()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		if cancel != nil {
			cancel() // SIGKILL через ctx
		}
	}
	return nil
}

// Status возвращает копию текущего состояния.
func (c *Cloudflared) Status() Status {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.status
}

func (c *Cloudflared) failWith(msg string) Status {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.status = Status{
		Provider:  ProviderTryCloudflare,
		Active:    false,
		LastError: msg,
	}
	return c.status
}

func (c *Cloudflared) killWith(reason string) {
	c.mu.Lock()
	cancel := c.cancel
	c.status = Status{
		Provider:  ProviderTryCloudflare,
		Active:    false,
		LastError: reason,
	}
	c.mu.Unlock()
	if cancel != nil {
		cancel()
	}
}

// ─── Subscribe / broadcast ─────────────────────────────────────────────────

// Subscribe возвращает канал, в который пушатся изменения статуса.
// Канал буферизован на 4 события; медленные читатели теряют события.
func (c *Cloudflared) Subscribe() <-chan Event {
	ch := make(chan Event, 4)
	c.subsMu.Lock()
	c.subs = append(c.subs, ch)
	c.subsMu.Unlock()
	return ch
}

func (c *Cloudflared) broadcast(s Status) {
	c.subsMu.Lock()
	defer c.subsMu.Unlock()
	for _, ch := range c.subs {
		select {
		case ch <- Event{Status: s}:
		default:
		}
	}
}

// Close останавливает туннель и закрывает все подписки.
func (c *Cloudflared) Close() error {
	_ = c.Stop()
	c.subsMu.Lock()
	for _, ch := range c.subs {
		close(ch)
	}
	c.subs = nil
	c.subsMu.Unlock()
	return nil
}
