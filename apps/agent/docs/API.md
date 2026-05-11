# VibeFly Agent API

Базовый URL в production: `http://127.0.0.1:3001`.

Авторизация — `Authorization: Bearer <token>`. Токен генерируется Android-приложением при первом запуске и прописывается в `/etc/vibefly/agent.toml`. При пустом токене в конфиге авторизация отключена (режим разработки).

## v0.1 — текущие эндпоинты

### `GET /health`

Открытая ручка. Работоспособность агента.

```json
{
  "status": "ok",
  "version": "0.0.1-dev",
  "time": "2026-05-11T18:00:00Z"
}
```

### `GET /system`

Метрики устройства.

```json
{
  "timestamp": "2026-05-11T18:00:00Z",
  "battery_level": 78,
  "battery_status": "Discharging",
  "temperature_c": 38.5,
  "cpu_percent": 23.4,
  "ram_used_mb": 2150,
  "ram_total_mb": 6144,
  "uptime_seconds": 312640
}
```

### `GET /apps`

Список приложений.

```json
[
  {
    "id": "amina-bot",
    "name": "amina-bot",
    "status": "running",
    "repo": "antsincgame/Amina-bot",
    "branch": "main",
    "port": 3001,
    "domain": "@AIAMINABOT",
    "memory_mb": 124,
    "started_at": "2026-05-08T03:58:00Z",
    "last_deploy": "2026-05-11T16:00:00Z"
  }
]
```

### `GET /apps/{id}`

Детали одного приложения. 404 если нет.

### `POST /apps/{id}/restart`

Переводит приложение в `running`. В v0.1 это имитация; в v0.2 будет `systemctl restart vibefly-app-<id>`.

```json
{ "status": "restarted", "id": "amina-bot" }
```

### `POST /apps/{id}/stop`

Переводит приложение в `stopped`.

## v0.2 — план

```
POST   /apps                              — создать из git URL
DELETE /apps/{id}                         — удалить
POST   /apps/{id}/start                   — start
POST   /apps/{id}/deploy                  — git pull + build + reload
GET    /apps/{id}/env                     — read env (с маскированием)
PUT    /apps/{id}/env                     — update env
GET    /apps/{id}/logs?lines=N            — последние N строк
```

## v0.3 — WebSocket

```
WS /apps/{id}/logs/stream    — лайв-стрим stdout/stderr
WS /system/stream            — лайв-метрики раз в 5–1 секунду
```

## v0.4–0.5 — AI tools

```
POST /ai/tools/{name}        — выполнить tool
```

List of tools и политика approval — в [docs/05-security-model.md](../../../docs/05-security-model.md).
