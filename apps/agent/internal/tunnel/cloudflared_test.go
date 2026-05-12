package tunnel

import "testing"

// TestTryCloudflareURLRegex — проверка что regex выдёргивает URL из реальных
// форматов вывода cloudflared. Тест критичен: если cloudflared поменяет
// формат, мы должны это поймать до релиза.
func TestTryCloudflareURLRegex(t *testing.T) {
	cases := []struct {
		name string
		line string
		want string
	}{
		{
			name: "banner with pipes",
			line: "2024-01-15T10:00:00Z INF |  https://big-cat-foxes-jump.trycloudflare.com  |",
			want: "https://big-cat-foxes-jump.trycloudflare.com",
		},
		{
			name: "inline message",
			line: "Visit it at: https://abc-def-123.trycloudflare.com (it may take some time)",
			want: "https://abc-def-123.trycloudflare.com",
		},
		{
			name: "hyphens only",
			line: "connection https://only-hyphens-here.trycloudflare.com established",
			want: "https://only-hyphens-here.trycloudflare.com",
		},
		{
			name: "no url",
			line: "INFO connection established",
			want: "",
		},
		{
			name: "wrong domain",
			line: "https://example.com is not a tunnel",
			want: "",
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := trycloudflareURLRe.FindString(tc.line)
			if got != tc.want {
				t.Errorf("FindString(%q) = %q, want %q", tc.line, got, tc.want)
			}
		})
	}
}

// TestNopManager — заглушка должна молча отказывать без паник.
func TestNopManager(t *testing.T) {
	var m Manager = Nop{}

	if s := m.Status(); s.Active || s.Provider != ProviderNone {
		t.Errorf("Nop.Status() = %+v, want inactive+none", s)
	}
	if err := m.Stop(); err != nil {
		t.Errorf("Nop.Stop() = %v, want nil", err)
	}
	if err := m.Close(); err != nil {
		t.Errorf("Nop.Close() = %v, want nil", err)
	}

	// Subscribe возвращает уже закрытый канал.
	sub := m.Subscribe()
	select {
	case _, ok := <-sub:
		if ok {
			t.Error("Nop.Subscribe() channel should be closed")
		}
	default:
		t.Error("Nop.Subscribe() channel should be readable (closed)")
	}
}
