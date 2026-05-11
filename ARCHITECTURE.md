# Архитектура VibeFly

Этот документ — техническая сводка. Полная версия в [docs/01-architecture.md](docs/01-architecture.md).

## Главный принцип

**Одно приложение для пользователя — три слоя внутри.** Пользователь скачивает APK и видит мобильный интерфейс. Внутри APK работает полноценная Linux-машина с systemd и нашим агентом.

## Три слоя

### 1. UI (Kotlin + Jetpack Compose)

Нативное Android-приложение. Содержит:

- Дашборд (метрики устройства, список приложений)
- Чат с AI-ассистентом
- Marketplace one-click приложений
- Деталки приложения (логи, env, deploys)
- Настройки (Cloudflare Tunnel, Tailscale, бэкапы, AI-провайдер)
- Foreground Service для удержания агента живым
- BootReceiver для автостарта после ребута

### 2. Agent (Go)

Живёт **внутри Debian-rootfs**, не в самом Android-приложении. Один статичный бинарник.

API (черновик):

```
GET   /health                         — статус устройства
GET   /apps                           — список приложений
POST  /apps                           — создать из git URL
GET   /apps/:id                       — детали
POST  /apps/:id/start                 — старт
POST  /apps/:id/stop                  — стоп
POST  /apps/:id/restart               — рестарт
POST  /apps/:id/deploy                — git pull + build + reload
GET   /apps/:id/logs?lines=N          — последние N строк
WS    /apps/:id/logs/stream           — лайв-стрим
GET   /apps/:id/env                   — read env (с маскированием)
PUT   /apps/:id/env                   — update
DELETE /apps/:id                      — удалить

GET   /metrics                        — Prometheus-формат
GET   /system                         — battery, temp, cpu, ram

POST  /ai/tools/:name                 — выполнить tool с verification
```

UI общается с агентом через `http://127.0.0.1:3001` (localhost внутри APK-sandbox).

### 3. Runtime (Debian rootfs + Droidspaces)

При первом запуске APK распаковывает `rootfs.img` (~500 МБ) в свою sandboxed-папку и запускает его через namespace-runtime (Droidspaces или собственный fallback).

В rootfs:

- Debian 12 ARM64 + systemd
- nodejs 22, python 3.12, go 1.22, git
- nginx, postgresql 16, redis 7 (опционально по выбору)
- cloudflared, tailscale
- наш Go-агент как systemd service

## Пути коммуникации

```
┌─ Android-приложение (APK) ────────────────────────┐
│                                                    │
│   UI (Compose)  ←→  Foreground Service             │
│                          ↓                         │
│                     запуск namespace-runtime       │
│                          ↓                         │
│   ┌─ Debian-rootfs ──────────────────────────┐    │
│   │                                            │    │
│   │  systemd  →  vibefly-agent.service        │    │
│   │              ↑                             │    │
│   │   UI → 127.0.0.1:3001 (через JNI/Unix sock)│   │
│   │                                            │    │
│   │  pm2 → user apps (на портах 4000+)        │    │
│   │                                            │    │
│   │  cloudflared → tunnel наружу              │    │
│   │                                            │    │
│   └────────────────────────────────────────────┘   │
│                                                    │
└────────────────────────────────────────────────────┘
```

## Безопасность

Всё в sandbox Android-приложения. Без root — ограниченный функционал (нет cgroups, нет систем-сервисов). С root через KernelSU — полный.

Данные пользователя живут в `/data/data/by.vibefly.app/`. Удаление приложения — удаление всех данных.

Ключи и токены в `EncryptedSharedPreferences` на Android-стороне, агент их получает через локальный handshake.

## Сетевая модель

- **Локальная (только в Wi-Fi):** UI на телефоне → агент на этом же телефоне.
- **Удалённый доступ:** Cloudflare Tunnel наружу + Tailscale для админ-доступа.
- **Cloud-режим (опционально):** агент держит WebSocket-соединение с `cloud.vibefly.dev`, можно управлять с любого устройства.

## Зависимости от Droidspaces

Droidspaces — open-source проект ([github.com/ravindu644/Droidspaces-OSS](https://github.com/ravindu644/Droidspaces-OSS)) под GPL v3. Используем как namespace-runtime для запуска Debian-rootfs с systemd. Альтернативы при провале — собственная обёртка над unshare(2) или fallback на proot.

Подробности в [runtime/droidspaces-notes/README.md](runtime/droidspaces-notes/README.md).
