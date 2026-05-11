// Package metrics — сбор метрик устройства.
//
// Читаем напрямую из /proc и /sys (на Android те же интерфейсы что в обычном Linux).
// На non-Linux (например при кросс-разработке на macOS) возвращаем синтетику, чтобы
// разработка UI не зависела от железа.
package metrics

import (
	"bufio"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"
)

// Snapshot — мгновенный срез состояния устройства.
type Snapshot struct {
	Timestamp     time.Time `json:"timestamp"`
	BatteryLevel  int       `json:"battery_level"`  // 0..100
	BatteryStatus string    `json:"battery_status"` // Charging / Discharging / Full / Unknown
	TemperatureC  float64   `json:"temperature_c"`  // средняя по thermal_zone
	CPUPercent    float64   `json:"cpu_percent"`    // 0..100 (loadavg/cores * 100)
	RAMUsedMB     int       `json:"ram_used_mb"`
	RAMTotalMB    int       `json:"ram_total_mb"`
	UptimeSeconds int       `json:"uptime_seconds"`
}

// Reader умеет отдавать Snapshot.
type Reader interface {
	Read() Snapshot
}

// New создаёт Reader подходящий под текущую ОС.
func New() Reader {
	if runtime.GOOS == "linux" {
		return linuxReader{}
	}
	return syntheticReader{}
}

// linuxReader читает реальные данные из /proc и /sys.
type linuxReader struct{}

func (linuxReader) Read() Snapshot {
	s := Snapshot{Timestamp: time.Now().UTC()}
	s.BatteryLevel, s.BatteryStatus = readBattery()
	s.TemperatureC = readTemperature()
	s.CPUPercent = readCPU()
	s.RAMUsedMB, s.RAMTotalMB = readRAM()
	s.UptimeSeconds = readUptime()
	return s
}

// syntheticReader для отладки на не-Linux хостах.
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
	}
}

// readBattery пытается прочитать первое /sys/class/power_supply/*/capacity.
func readBattery() (int, string) {
	entries, err := os.ReadDir("/sys/class/power_supply")
	if err != nil {
		return 0, "Unknown"
	}
	for _, e := range entries {
		capacityPath := "/sys/class/power_supply/" + e.Name() + "/capacity"
		data, err := os.ReadFile(capacityPath)
		if err != nil {
			continue
		}
		level, err := strconv.Atoi(strings.TrimSpace(string(data)))
		if err != nil {
			continue
		}
		statusPath := "/sys/class/power_supply/" + e.Name() + "/status"
		statusBytes, _ := os.ReadFile(statusPath)
		status := strings.TrimSpace(string(statusBytes))
		if status == "" {
			status = "Unknown"
		}
		return level, status
	}
	return 0, "Unknown"
}

// readTemperature берёт среднюю по всем thermal_zone.
func readTemperature() float64 {
	entries, err := os.ReadDir("/sys/class/thermal")
	if err != nil {
		return 0
	}
	var sum float64
	var count int
	for _, e := range entries {
		if !strings.HasPrefix(e.Name(), "thermal_zone") {
			continue
		}
		data, err := os.ReadFile("/sys/class/thermal/" + e.Name() + "/temp")
		if err != nil {
			continue
		}
		raw, err := strconv.Atoi(strings.TrimSpace(string(data)))
		if err != nil {
			continue
		}
		// Ядро отдаёт температуру в милли-градусах.
		sum += float64(raw) / 1000.0
		count++
	}
	if count == 0 {
		return 0
	}
	return sum / float64(count)
}

// readCPU считает грубую загрузку через /proc/loadavg.
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

// readRAM достаёт MemTotal и MemAvailable из /proc/meminfo.
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
		val, err := strconv.Atoi(fields[1]) // в килобайтах
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

// readUptime читает /proc/uptime.
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
