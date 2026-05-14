// Package metrics — сбор метрик устройства.
//
// Читаем напрямую из /proc и /sys (на Android те же интерфейсы что в обычном Linux).
// При недоступе (SELinux) пробуем через root если он доступен.
package metrics

import (
	"bufio"
	"bytes"
	"context"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"

	"by.vibefly/agent/internal/rootx"
)

type Snapshot struct {
	Timestamp     time.Time `json:"timestamp"`
	BatteryLevel  int       `json:"battery_level"`
	BatteryStatus string    `json:"battery_status"`
	TemperatureC  float64   `json:"temperature_c"`
	CPUPercent    float64   `json:"cpu_percent"`
	RAMUsedMB     int       `json:"ram_used_mb"`
	RAMTotalMB    int       `json:"ram_total_mb"`
	UptimeSeconds int       `json:"uptime_seconds"`
	RootAvailable bool      `json:"root_available"`
}

type Reader interface {
	Read() Snapshot
}

func New() Reader {
	if runtime.GOOS == "linux" {
		return linuxReader{}
	}
	return syntheticReader{}
}

type linuxReader struct{}

func (linuxReader) Read() Snapshot {
	s := Snapshot{Timestamp: time.Now().UTC()}
	s.BatteryLevel, s.BatteryStatus = readBattery()
	s.TemperatureC = readTemperature()
	s.CPUPercent = readCPU()
	s.RAMUsedMB, s.RAMTotalMB = readRAM()
	s.UptimeSeconds = readUptime()
	s.RootAvailable = rootx.Available()
	return s
}

type syntheticReader struct{}

func (syntheticReader) Read() Snapshot {
	return Snapshot{
		Timestamp:     time.Now().UTC(),
		BatteryLevel:  78,
		BatteryStatus: "Discharging",
		TemperatureC:  38.5,
		CPUPercent:    23.4,
		RAMUsedMB:     2150,
		RAMTotalMB:    6144,
		UptimeSeconds: 312640,
		RootAvailable: false,
	}
}

func readBattery() (int, string) {
	ctx, cancel := context.WithTimeout(context.Background(), 1*time.Second)
	defer cancel()

	entries, err := os.ReadDir("/sys/class/power_supply")
	if err != nil {
		if rootx.Available() {
			if out, runErr := rootx.Run(ctx, "ls /sys/class/power_supply"); runErr == nil {
				for _, name := range strings.Fields(string(out)) {
					if level, status, ok := tryBatteryEntry(ctx, name); ok {
						return level, status
					}
				}
			}
		}
		return 0, "Unknown"
	}
	for _, e := range entries {
		if level, status, ok := tryBatteryEntry(ctx, e.Name()); ok {
			return level, status
		}
	}
	return 0, "Unknown"
}

func tryBatteryEntry(ctx context.Context, name string) (int, string, bool) {
	capacityPath := "/sys/class/power_supply/" + name + "/capacity"
	data, err := rootx.ReadFile(ctx, capacityPath)
	if err != nil {
		return 0, "", false
	}
	level, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil {
		return 0, "", false
	}
	statusPath := "/sys/class/power_supply/" + name + "/status"
	statusBytes, _ := rootx.ReadFile(ctx, statusPath)
	status := strings.TrimSpace(string(statusBytes))
	if status == "" {
		status = "Unknown"
	}
	return level, status, true
}

func readTemperature() float64 {
	ctx, cancel := context.WithTimeout(context.Background(), 1*time.Second)
	defer cancel()

	entries, err := os.ReadDir("/sys/class/thermal")
	if err != nil {
		if rootx.Available() {
			return readTemperatureViaRoot(ctx)
		}
		return 0
	}
	var sum float64
	var count int
	for _, e := range entries {
		if !strings.HasPrefix(e.Name(), "thermal_zone") {
			continue
		}
		data, err := rootx.ReadFile(ctx, "/sys/class/thermal/"+e.Name()+"/temp")
		if err != nil {
			continue
		}
		raw, err := strconv.Atoi(strings.TrimSpace(string(data)))
		if err != nil {
			continue
		}
		sum += float64(raw) / 1000.0
		count++
	}
	if count == 0 {
		return 0
	}
	return sum / float64(count)
}

func readTemperatureViaRoot(ctx context.Context) float64 {
	out, err := rootx.Run(ctx, "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null")
	if err != nil {
		return 0
	}
	var sum float64
	var count int
	for _, line := range bytes.Split(out, []byte("\n")) {
		raw, err := strconv.Atoi(strings.TrimSpace(string(line)))
		if err != nil {
			continue
		}
		sum += float64(raw) / 1000.0
		count++
	}
	if count == 0 {
		return 0
	}
	return sum / float64(count)
}

func readCPU() float64 {
	data, err := os.ReadFile("/proc/loadavg")
	if err != nil {
		return 0
	}
	fields := strings.Fields(string(data))
	if len(fields) == 0 {
		return 0
	}
	load, err := strconv.ParseFloat(fields[0], 64)
	if err != nil {
		return 0
	}
	cores := runtime.NumCPU()
	if cores == 0 {
		return 0
	}
	pct := load / float64(cores) * 100.0
	if pct > 100 {
		pct = 100
	}
	return pct
}

func readRAM() (usedMB, totalMB int) {
	f, err := os.Open("/proc/meminfo")
	if err != nil {
		return 0, 0
	}
	defer f.Close()

	var total, available int
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := scanner.Text()
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		val, err := strconv.Atoi(fields[1])
		if err != nil {
			continue
		}
		switch fields[0] {
		case "MemTotal:":
			total = val
		case "MemAvailable:":
			available = val
		}
	}
	if total == 0 {
		return 0, 0
	}
	return (total - available) / 1024, total / 1024
}

func readUptime() int {
	data, err := os.ReadFile("/proc/uptime")
	if err != nil {
		return 0
	}
	fields := strings.Fields(string(data))
	if len(fields) == 0 {
		return 0
	}
	sec, err := strconv.ParseFloat(fields[0], 64)
	if err != nil {
		return 0
	}
	return int(sec)
}
