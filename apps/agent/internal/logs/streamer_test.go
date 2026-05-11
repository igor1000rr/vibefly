package logs

import (
	"context"
	"io"
	"log/slog"
	"testing"
	"time"
)

func newTestStreamer() *Streamer {
	return NewStreamer(slog.New(slog.NewTextHandler(io.Discard, nil)), 100)
}

func TestStreamer_AppendAndRecent(t *testing.T) {
	s := newTestStreamer()
	for i := 0; i < 5; i++ {
		s.Append(Entry{App: "a", Level: LevelInfo, Message: "x"})
	}
	if got := len(s.Recent("a", 10)); got != 5 {
		t.Errorf("ожидали 5 записей, получили %d", got)
	}
	if got := len(s.Recent("a", 3)); got != 3 {
		t.Errorf("ожидали 3 последних, получили %d", got)
	}
}

func TestStreamer_CapacityTrim(t *testing.T) {
	s := NewStreamer(slog.New(slog.NewTextHandler(io.Discard, nil)), 3)
	for i := 0; i < 10; i++ {
		s.Append(Entry{App: "a", Message: "x"})
	}
	if got := len(s.Recent("a", 100)); got != 3 {
		t.Errorf("ожидали 3 после trim, получили %d", got)
	}
}

func TestStreamer_Subscribe(t *testing.T) {
	s := newTestStreamer()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	ch := s.Subscribe(ctx, "a")
	s.Append(Entry{App: "a", Message: "first"})

	select {
	case e := <-ch:
		if e.Message != "first" {
			t.Errorf("получили %q", e.Message)
		}
	case <-time.After(time.Second):
		t.Fatal("таймаут ожидания записи")
	}
}
