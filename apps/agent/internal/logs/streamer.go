// Package logs — хранение и стрим логов приложений.
//
// Persistence: если передан logs_dir, каждое приложение пишет line-delimited
// JSON в <logs_dir>/<app>.log. При старте агента последние N строк
// восстанавливаются в память — юзер открывает AppDetail и сразу видит бэклог.
//
// Ротация: если файл превысил maxLogFileBytes — переименовываем в .log.1
// (старый .log.1 удаляется) и начинаем новый .log. Проверка на каждом Append —
// дешёво потому что мы уже следим за размером файла в памяти.
package logs

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

type Level string

const (
	LevelInfo  Level = "info"
	LevelWarn  Level = "warn"
	LevelError Level = "error"
)

// maxLogFileBytes — лимит одного .log файла перед ротацией. 5 MB — хватит на
// весь день verbose-логов для почти любого бэкенда. С .log.1 итого до 10 MB на приложение.
const maxLogFileBytes int64 = 5 * 1024 * 1024

type Entry struct {
	Time    time.Time `json:"time"`
	App     string    `json:"app"`
	Level   Level     `json:"level"`
	Source  string    `json:"source"`
	Message string    `json:"message"`
}

type Streamer struct {
	mu        sync.RWMutex
	buffers   map[string][]Entry
	capacity  int
	listeners map[string]map[chan Entry]struct{}
	logger    *slog.Logger

	// Persistence — если logsDir != "".
	logsDir  string
	fileMu   sync.Mutex            // защищает от параллельных записей в один и тот же .log
	fileSize map[string]int64      // кэш размеров файлов для ротации без os.Stat каждый раз
}

func NewStreamer(logger *slog.Logger, capacity int) *Streamer {
	return NewStreamerWithDir(logger, capacity, "")
}

// NewStreamerWithDir — вариант с persistence. logsDir — корневая директория.
// При старте сканируется, восстанавливаются последние capacity строк на приложение.
func NewStreamerWithDir(logger *slog.Logger, capacity int, logsDir string) *Streamer {
	if capacity <= 0 {
		capacity = 500
	}
	s := &Streamer{
		buffers:   make(map[string][]Entry),
		capacity:  capacity,
		listeners: make(map[string]map[chan Entry]struct{}),
		logger:    logger,
		logsDir:   logsDir,
		fileSize:  make(map[string]int64),
	}
	if logsDir != "" {
		if err := os.MkdirAll(logsDir, 0o755); err != nil {
			logger.Warn("logs persistence: не удалось создать logs_dir", "path", logsDir, "err", err)
		} else {
			s.loadFromDisk()
		}
	}
	return s
}

// loadFromDisk — сканирует logsDir, ищет *.log, восстанавливает по capacity
// строк на приложение. Битые JSON-строки пропускает без краша.
func (s *Streamer) loadFromDisk() {
	entries, err := os.ReadDir(s.logsDir)
	if err != nil {
		s.logger.Warn("logs persistence: не удалось открыть logs_dir", "err", err)
		return
	}
	restored := 0
	for _, e := range entries {
		name := e.Name()
		if e.IsDir() || !strings.HasSuffix(name, ".log") {
			continue
		}
		appID := strings.TrimSuffix(name, ".log")
		path := filepath.Join(s.logsDir, name)

		file, err := os.Open(path)
		if err != nil {
			s.logger.Warn("logs persistence: open", "path", path, "err", err)
			continue
		}
		lines := tailLines(file, s.capacity)
		_ = file.Close()

		if stat, err := os.Stat(path); err == nil {
			s.fileSize[appID] = stat.Size()
		}

		buf := make([]Entry, 0, len(lines))
		for _, line := range lines {
			if line == "" {
				continue
			}
			var entry Entry
			if err := json.Unmarshal([]byte(line), &entry); err != nil {
				continue
			}
			buf = append(buf, entry)
		}
		if len(buf) > 0 {
			s.buffers[appID] = buf
			restored += len(buf)
		}
	}
	if restored > 0 {
		s.logger.Info("logs persistence: восстановлены", "lines", restored, "apps", len(s.buffers))
	}
}

// tailLines — читает всё из r, возвращает последние limit строк. Простой
// scanner-подход: для 5 MB файла это ~100ms на телефоне — приемлемо для старта.
func tailLines(file *os.File, limit int) []string {
	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	ring := make([]string, 0, limit)
	for scanner.Scan() {
		if len(ring) >= limit {
			ring = ring[1:]
		}
		ring = append(ring, scanner.Text())
	}
	return ring
}

func (s *Streamer) Append(e Entry) {
	s.mu.Lock()
	buf := s.buffers[e.App]
	buf = append(buf, e)
	if len(buf) > s.capacity {
		buf = buf[len(buf)-s.capacity:]
	}
	s.buffers[e.App] = buf
	listeners := s.listeners[e.App]
	s.mu.Unlock()

	for ch := range listeners {
		select {
		case ch <- e:
		default:
		}
	}

	if s.logsDir != "" {
		s.persistEntry(e)
	}
}

// persistEntry — дописывает строку в <logs_dir>/<app>.log. При превышении
// maxLogFileBytes ротирует. Ошибки логируются, не прерывают hot path.
func (s *Streamer) persistEntry(e Entry) {
	data, err := json.Marshal(e)
	if err != nil {
		return
	}
	data = append(data, '\n')

	s.fileMu.Lock()
	defer s.fileMu.Unlock()

	path := filepath.Join(s.logsDir, e.App+".log")

	// Проверяем размер по кэшу (обновляется при каждой записи). Если кэш пуст (первая
	// запись для этого приложения) — stat'им файл раз.
	size, hasSize := s.fileSize[e.App]
	if !hasSize {
		if stat, err := os.Stat(path); err == nil {
			size = stat.Size()
		}
	}
	if size > maxLogFileBytes {
		rotated := path + ".1"
		_ = os.Remove(rotated)
		if err := os.Rename(path, rotated); err != nil {
			s.logger.Warn("logs rotation failed", "app", e.App, "err", err)
		}
		size = 0
	}

	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		s.logger.Warn("logs persist: open", "app", e.App, "err", err)
		return
	}
	n, werr := f.Write(data)
	_ = f.Close()
	if werr != nil {
		s.logger.Warn("logs persist: write", "app", e.App, "err", werr)
		return
	}
	s.fileSize[e.App] = size + int64(n)
}

// AppendLine — упрощённый интерфейс для ExecSupervisor (supervisor.LogSink).
func (s *Streamer) AppendLine(appID, source, line string) {
	level := LevelInfo
	lower := strings.ToLower(line)
	switch {
	case strings.Contains(lower, "error") || strings.Contains(lower, "panic") || strings.Contains(lower, "fatal"):
		level = LevelError
	case strings.Contains(lower, "warn"):
		level = LevelWarn
	}
	s.Append(Entry{
		Time:    time.Now().UTC(),
		App:     appID,
		Level:   level,
		Source:  source,
		Message: line,
	})
}

func (s *Streamer) Recent(app string, lines int) []Entry {
	s.mu.RLock()
	defer s.mu.RUnlock()

	buf := s.buffers[app]
	if lines <= 0 || lines >= len(buf) {
		out := make([]Entry, len(buf))
		copy(out, buf)
		return out
	}
	out := make([]Entry, lines)
	copy(out, buf[len(buf)-lines:])
	return out
}

func (s *Streamer) Subscribe(ctx context.Context, app string) <-chan Entry {
	ch := make(chan Entry, 32)

	s.mu.Lock()
	lst, ok := s.listeners[app]
	if !ok {
		lst = make(map[chan Entry]struct{})
		s.listeners[app] = lst
	}
	lst[ch] = struct{}{}
	s.mu.Unlock()

	go func() {
		<-ctx.Done()
		s.mu.Lock()
		delete(s.listeners[app], ch)
		s.mu.Unlock()
		close(ch)
	}()
	return ch
}

func (s *Streamer) StartFakeGenerator(ctx context.Context, apps []string) {
	if len(apps) == 0 {
		return
	}
	go func() {
		rng := rand.New(rand.NewSource(time.Now().UnixNano()))
		ticker := time.NewTicker(2 * time.Second)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				return
			case t := <-ticker.C:
				app := apps[rng.Intn(len(apps))]
				s.Append(buildFakeEntry(rng, app, t))
			}
		}
	}()
}

var fakeSources = []string{"grammy", "appwrite", "router", "http", "elevenlabs", "lirax"}

var fakeMessages = []struct {
	level Level
	text  string
}{
	{LevelInfo, "POST /webhook 200 \u00B7 12ms"},
	{LevelInfo, "POST /webhook 200 \u00B7 8ms"},
	{LevelInfo, "conversation #%d saved"},
	{LevelInfo, "claude-haiku \u00B7 %d tok"},
	{LevelWarn, "rate-limit 80%%"},
	{LevelInfo, "failover \u2192 cerebras"},
	{LevelInfo, "tts \u00B7 3.2s audio"},
	{LevelError, "upstream timeout after 5s"},
}

func buildFakeEntry(rng *rand.Rand, app string, t time.Time) Entry {
	tpl := fakeMessages[rng.Intn(len(fakeMessages))]
	msg := tpl.text
	switch {
	case containsVerb(msg, "%d"):
		msg = fmt.Sprintf(msg, rng.Intn(9000)+100)
	}
	return Entry{
		Time:    t.UTC(),
		App:     app,
		Level:   tpl.level,
		Source:  fakeSources[rng.Intn(len(fakeSources))],
		Message: msg,
	}
}

func containsVerb(s, verb string) bool {
	for i := 0; i+len(verb) <= len(s); i++ {
		if s[i:i+len(verb)] == verb {
			return true
		}
	}
	return false
}
