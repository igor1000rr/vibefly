# VibeFly Agent

Go-агент. Живёт внутри Debian rootfs как systemd-сервис и слушает на `127.0.0.1:3001`. Android-приложение в локальной петле общается с ним через REST + WebSocket.

## Статус

v0.1 — каркас собран, билдится, запускается. Часть ручек на фейк-данных. Подробности в [docs/API.md](docs/API.md).

## Структура

```
apps/agent/
├── cmd/agent/main.go              — точка входа
├── internal/
│   ├── config/    — TOML-конфиг
│   ├── server/    — chi-роутер + middleware
│   ├── apps/      — in-memory store приложений
│   └── metrics/   — чтение /proc и /sys (с fallback на синтетику на non-Linux)
├── config/
│   ├── agent.example.toml
│   └── vibefly-agent.service     — systemd unit для rootfs
├── docs/API.md
└── Makefile
```

## Как запустить локально (на десктопе)

Нужен Go 1.22+.

```bash
cd apps/agent
go mod tidy
make run
```

Проверь в другом терминале:

```bash
curl -s localhost:3001/health | jq
curl -s localhost:3001/system | jq
curl -s localhost:3001/apps | jq
curl -s localhost:3001/apps/amina-bot | jq
curl -s -X POST localhost:3001/apps/amina-bot/restart | jq
```

На macOS / Windows метрики вернутся синтетические (это нормально для разработки UI). Реальные — только на Linux/Android.

## Сборка под ARM64

```bash
make arm64
# → bin/vibefly-agent-arm64
```

Этот бинарник пойдёт в Debian-rootfs, который впоследствии упакуется в APK.

## Роудмап

- v0.1 (сейчас): /health, /system, /apps, restart/stop на фейк-сторе
- v0.2: реальный supervisor поверх systemd, deploy from git
- v0.3: WebSocket-стрим логов, env-management
- v0.4: AI tools API (read-only)
- v0.5: AI tools API (approval-based mutations)

## Тесты

```bash
make test
```
