// Package logs — хранение и стрим логов приложений.
package logs

import (
	"context"
	"fmt"
	"log/slog"
	"math/rand"
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
}

func NewStreamer(logger *slog.Logger, capacity int) *Streamer {
	if capacity <= 0 {
		capacity = 500
	}
	return &Streamer{
		buffers:   make(map[string][]Entry),
		capacity:  capacity,
		listeners: make(map[string]map[chan Entry]struct{}),
		logger:    logger,
	}
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
}

// AppendLine — упрощённый интерфейс для ExecSupervisor (supervisor.LogSink).
// Строит Entry из строки, определяет level по ключевым словам (warn/error/panic).
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
