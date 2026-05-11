# Агент

Go-агент, живёт внутри Debian-rootfs как systemd-сервис. См. [docs/01-architecture.md](../../docs/01-architecture.md).

## Статус

Не начат. Планируется к фазе 1.

## Первые эндпоинты

```
GET  /health
GET  /apps
POST /apps/:id/restart
GET  /apps/:id/logs?lines=100
```

## Стек

- Go 1.22+
- chi router
- nhooyr.io/websocket
- sqlx + SQLite

## Как запустить (позже)

```bash
make build
./bin/vibefly-agent --config /etc/vibefly/agent.toml
```

## API documentation

Will be in `docs/API.md` once endpoints stabilize.
