package supervisor

import (
	"strings"
	"testing"
)

func TestValidateID(t *testing.T) {
	cases := map[string]bool{
		"amina-bot":      true,
		"foo_bar_123":    true,
		"":               false,
		"with space":     false,
		"with/slash":     false,
		"with;semicolon": false,
		"with.dot":       false,
	}
	for input, wantOk := range cases {
		err := validateID(input)
		if (err == nil) != wantOk {
			t.Errorf("validateID(%q): wantOk=%v, err=%v", input, wantOk, err)
		}
	}
}

func TestRenderUnit_basics(t *testing.T) {
	unit := renderUnit(AppSpec{
		ID:         "amina-bot",
		Name:       "amina-bot",
		WorkingDir: "/var/lib/vibefly/apps/amina-bot",
		StartCmd:   "node index.js",
		MemoryMax:  "512M",
		CPUQuota:   "80%",
		Env: map[string]string{
			"NODE_ENV":  "production",
			"BOT_TOKEN": "secret",
			"bad key":   "skipped",
		},
	})

	for _, expect := range []string{
		"Description=VibeFly app amina-bot",
		"WorkingDirectory=/var/lib/vibefly/apps/amina-bot",
		"ExecStart=/bin/sh -lc",
		"MemoryMax=512M",
		"CPUQuota=80%",
		"NoNewPrivileges=yes",
		"SyslogIdentifier=vibefly-app-amina-bot",
		"WantedBy=multi-user.target",
	} {
		if !strings.Contains(unit, expect) {
			t.Errorf("unit missing %q\n%s", expect, unit)
		}
	}

	if strings.Contains(unit, "bad key") {
		t.Error("unsafe env key попал в unit")
	}
}

func TestParseShow(t *testing.T) {
	input := strings.Join([]string{
		"ActiveState=active",
		"SubState=running",
		"ExecMainStartTimestamp=Mon 2026-05-11 18:14:33 UTC",
		"MemoryCurrent=134217728",
		"CPUUsageNSec=2500000000",
		"ExecMainStatus=0",
	}, "\n")
	st := parseShow(input)
	if st.Active != StatusRunning {
		t.Errorf("ожидали running, получили %s", st.Active)
	}
	if st.MemoryMB != 128 {
		t.Errorf("ожидали 128MB, получили %d", st.MemoryMB)
	}
	if st.CPUSec < 2.4 || st.CPUSec > 2.6 {
		t.Errorf("ожидали ~2.5 sec, получили %f", st.CPUSec)
	}
}

func TestIsSafeEnvKey(t *testing.T) {
	if !isSafeEnvKey("FOO_BAR") {
		t.Error("FOO_BAR should be safe")
	}
	if isSafeEnvKey("FOO BAR") {
		t.Error("FOO BAR should not be safe")
	}
	if isSafeEnvKey("") {
		t.Error("empty should not be safe")
	}
}

func TestNopSupervisor(t *testing.T) {
	n := &NopSupervisor{reason: "test"}
	if n.Available() {
		t.Error("NopSupervisor.Available() должен возвращать false")
	}
}
