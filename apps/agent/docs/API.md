# VibeFly Agent API

Базовый URL в production: `http://127.0.0.1:3001`.

Авторизация — `Authorization: Bearer <token>`. Токен генерируется Android-приложением при первом запуске и прописывается в `/etc/vibefly/agent.toml`. При пустом токене в конфиге авторизация отключена (режим разработки).

## v0.3 — текущие эндпоинты

### `GET /health`

Открытая ручка. Работоспособность агента + флаг доступности supervisor'а.

```json
{
  "status": "ok",
  "version": "0.0.3-dev",
  "time": "2026-05-11T18:00:00Z",
  "supervisor_available": true
}
```

`supervisor_available: true` означает, что агент запущен на Linux с systemd и может реально управлять приложениями. `false` — demo-режим с фейк-данными.

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

Список приложений. При доступном supervisor'е статусы синхронизированы с systemd.

### `GET /apps/{id}`

Детали одного приложения.

### `POST /apps`

Регистрирует новое приложение и устанавливает его как systemd unit.

```json
{
  "id": "my-bot",
  "name": "My Bot",
  "start_cmd": "node index.js",
  "env": { "BOT_TOKEN": "..." },
  "memory_max": "512M",
  "port": 5000,
  "domain": "bot.example.com"
}
```

Обязательные поля: `id` (slug `[a-zA-Z0-9_-]{1,64}`), `start_cmd`.

### `DELETE /apps/{id}`

Останавливает, делает `systemctl disable` и удаляет unit-файл.

### `POST /apps/{id}/start`, `POST /apps/{id}/restart`, `POST /apps/{id}/stop`

Управление состоянием приложения через `systemctl`.

### `GET /apps/{id}/logs?lines=N`

Последние N записей лога (по умолчанию 100). Запись:

```json
{
  "time": "2026-05-11T09:41:14Z",
  "app": "amina-bot",
  "level": "info",
  "source": "grammy",
  "message": "POST /webhook 200 · 12ms"
}
```

### `WS /apps/{id}/logs/stream`

WebSocket-стрим логов. Сначала отдаётся backlog последних 100 записей.

Дальше:

- если supervisor доступен — лайв `journalctl -fu vibefly-app-<id>` с автоклассификацией level по содержимому строки;
- если supervisor — `NopSupervisor` (например, macOS-десктоп) — fallback на in-memory pub/sub с фейк-генератором.

## Marketplace v0.1

### `GET /marketplace`

Список встроенных one-click шаблонов.

```json
[
  {
    "id": "vaultwarden",
    "name": "Vaultwarden",
    "category": "privacy",
    "icon": "🔐",
    "description": "Менеджер паролей с поддержкой Bitwarden API.",
    "image": "vaultwarden/server:latest",
    "start_cmd": "vaultwarden",
    "default_port": 8080,
    "memory_max": "256M",
    "env_schema": [
      { "key": "ADMIN_TOKEN", "label": "Admin token", "secret": true, "required": true }
    ],
    "tags": ["password-manager", "selfhosted"]
  }
]
```

### `GET /marketplace/{id}`

Детали одного шаблона. 404 если нет.

### `POST /marketplace/{id}/install`

One-click install. Параметры тела опциональные — без них приложение установится с дефолтами шаблона.

```json
{
  "app_id": "my-vault",
  "domain": "vault.example.com",
  "port": 8080,
  "env": { "ADMIN_TOKEN": "secret" }
}
```

После успешного install приложение появится в `GET /apps` со статусом `stopped`. Запустить нужно вручную через `POST /apps/{id}/start`.

## v0.4 — план

```
POST   /apps/{id}/deploy                  — git pull + build + reload
GET    /apps/{id}/env                     — read env (с маскированием)
PUT    /apps/{id}/env                     — update env
WS     /system/stream                     — лайв-метрики
```

## v0.5 — AI tools

```
POST /ai/tools/{name}        — выполнить tool
```

List of tools и политика approval — в [docs/05-security-model.md](../../../docs/05-security-model.md).
