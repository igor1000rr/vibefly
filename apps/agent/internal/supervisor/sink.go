package supervisor

// LogSink — интерфейс для записи stdout/stderr в внешний buffer (обычно logs.Streamer).
//
// Используется ExecSupervisor: при старте приложения каждая строка вывода
// дублируется в LogSink, чтобы GET /apps/{id}/logs?lines=100 мог вернуть backlog
// при открытии AppDetailScreen — без него экран показывал только новые строки
// из WebSocket-стрима, без истории.
//
// Пакет supervisor НЕ должен импортировать logs (можно получить circular import
// в будущем) — поэтому LogSink живёт здесь, а logs.Streamer реализует этот
// интерфейс неявно (duck typing).
type LogSink interface {
	AppendLine(appID, source, line string)
}
