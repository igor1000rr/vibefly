package supervisor

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"

	"by.vibefly/agent/internal/rootx"
)

// cgroupBase — cgroup v2 корень. На Android 13+ это стандарт.
const cgroupBase = "/sys/fs/cgroup"

// applyCgroupLimits — создаёт cgroup для приложения, пишет memory.max и cpu.max,
// перемещает в неё процесс через cgroup.procs.
//
// Требует root: без root /sys/fs/cgroup не writable. При отсутствии root —
// логируем debug и возвращаем nil (не ошибка — limits opt-in).
//
// MemoryMax форматы: "512M", "1G", "256000K", "0" = unlimited.
// CPUQuota форматы: "50%" → "50000 100000" (50ms из каждых 100ms),
//                  "200%" → "200000 100000" (2 ядра).
func applyCgroupLimits(logger *slog.Logger, pid int, spec AppSpec) error {
	if runtime.GOOS != "linux" {
		return nil
	}
	if spec.MemoryMax == "" && spec.CPUQuota == "" {
		return nil
	}
	if !rootx.Available() {
		logger.Debug("cgroup limits игнорируются: root недоступен",
			"id", spec.ID, "mem", spec.MemoryMax, "cpu", spec.CPUQuota)
		return nil
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	cgroupDir := cgroupBase + "/vibefly-" + spec.ID

	if _, err := rootx.Run(ctx, "mkdir -p "+cgroupDir); err != nil {
		return fmt.Errorf("mkdir cgroup: %w", err)
	}

	if spec.MemoryMax != "" {
		memBytes := parseMemoryMax(spec.MemoryMax)
		if memBytes != "" {
			if err := rootx.WriteFile(ctx, cgroupDir+"/memory.max", memBytes); err != nil {
				logger.Warn("cgroup memory.max write failed",
					"id", spec.ID, "value", memBytes, "err", err)
			}
		}
	}

	if spec.CPUQuota != "" {
		cpuVal := parseCPUQuota(spec.CPUQuota)
		if cpuVal != "" {
			if err := rootx.WriteFile(ctx, cgroupDir+"/cpu.max", cpuVal); err != nil {
				logger.Warn("cgroup cpu.max write failed",
					"id", spec.ID, "value", cpuVal, "err", err)
			}
		}
	}

	if err := rootx.WriteFile(ctx, cgroupDir+"/cgroup.procs", strconv.Itoa(pid)); err != nil {
		return fmt.Errorf("cgroup.procs write: %w", err)
	}

	logger.Info("cgroup limits applied",
		"id", spec.ID, "pid", pid, "mem", spec.MemoryMax, "cpu", spec.CPUQuota, "dir", cgroupDir)
	return nil
}

func cleanupCgroup(logger *slog.Logger, appID string) {
	if runtime.GOOS != "linux" || !rootx.Available() {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	cgroupDir := cgroupBase + "/vibefly-" + appID
	if _, err := os.Stat(cgroupDir); err != nil {
		return
	}
	if _, err := rootx.Run(ctx, "rmdir "+cgroupDir); err != nil {
		logger.Debug("cgroup rmdir failed (ок если остались процессы)",
			"id", appID, "err", err)
	}
}

func parseMemoryMax(input string) string {
	input = strings.TrimSpace(input)
	if input == "" || input == "0" || strings.EqualFold(input, "max") {
		return "max"
	}
	var mul int64 = 1
	switch last := input[len(input)-1]; last {
	case 'K', 'k':
		mul = 1024
		input = input[:len(input)-1]
	case 'M', 'm':
		mul = 1024 * 1024
		input = input[:len(input)-1]
	case 'G', 'g':
		mul = 1024 * 1024 * 1024
		input = input[:len(input)-1]
	}
	n, err := strconv.ParseInt(strings.TrimSpace(input), 10, 64)
	if err != nil || n <= 0 {
		return ""
	}
	return strconv.FormatInt(n*mul, 10)
}

func parseCPUQuota(input string) string {
	input = strings.TrimSpace(input)
	if !strings.HasSuffix(input, "%") {
		return ""
	}
	num, err := strconv.Atoi(strings.TrimSuffix(input, "%"))
	if err != nil || num <= 0 {
		return ""
	}
	quota := num * 1000
	return fmt.Sprintf("%d 100000", quota)
}
