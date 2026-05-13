package supervisor

// StartSpec — вариант Start с явным AppSpec. Нужен для ExecSupervisor, который
// не хранит unit-файлы на диске и должен получить StartCmd из Store. Для
// SystemdSupervisor просто делегируется на Start потому что там всё в unit-файле.

import "context"

// Расширенный интерфейс — для реализаций, которым нужен полный AppSpec при старте.
type SpecStarter interface {
	StartSpec(ctx context.Context, spec AppSpec) error
}

// SystemdSupervisor и NopSupervisor не имплементируют SpecStarter явно —
// Store делает type assertion и fallback на Start(id) если интерфейс не реализован.
//
// ExecSupervisor.StartSpec уже реализован как StartWithSpec — переименовываем ради
// явного соответствия интерфейсу.
func (s *ExecSupervisor) StartSpec(ctx context.Context, spec AppSpec) error {
	return s.startWithSpec(ctx, spec)
}
