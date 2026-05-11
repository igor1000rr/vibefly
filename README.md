# VibeFly

> Phone-as-a-Server. Превращает Android-смартфон в полноценный PaaS с AI-ассистентом.

VibeFly — это одно мобильное приложение, которое превращает обычный смартфон в self-hosted сервер для веб-приложений, ботов, баз данных и автоматизаций. Никакого Termux, никакого ssh, никаких proot-команд. Установил APK — открыл — задеплоил репозиторий из GitHub — получил живой сайт на своём устройстве, доступный из интернета через Cloudflare Tunnel.

## Что это решает

- **VPS стоит денег.** Старый телефон уже есть в ящике стола.
- **Self-hosting сложен.** Coolify, Dokploy, CapRover требуют сервер, Docker, доменное имя, ssh. VibeFly — один тап.
- **AI-ассистенты только пишут код.** Bolt, Lovable, v0 генерируют сайт, но дальше пользователь сам разбирается с хостингом и багами. VibeFly умеет и хостить, и поддерживать.

## Архитектура

VibeFly — это **вариант C** (см. [docs/01-architecture.md](docs/01-architecture.md)): один APK с тремя слоями внутри.

```
┌─────────────────────────────────────┐
│  VibeFly UI                          │  ← Kotlin + Jetpack Compose
│  Дашборд, чат с AI, маркетплейс      │
├─────────────────────────────────────┤
│  VibeFly Agent (foreground service) │  ← Go
│  REST + WebSocket + AI tools         │
├─────────────────────────────────────┤
│  Embedded Linux Runtime             │  ← Droidspaces / custom
│  Debian 12 + systemd + PM2-стек     │
└─────────────────────────────────────┘
```

Для пользователя это **одно приложение**. Внутри полноценная Linux-машина с systemd, базами, runtime'ами и нашим агентом.

## Статус

**Фаза 0 — Proof of Concept.** Проверка совместимости железа, dogfooding на личном устройстве. Подробности — [ROADMAP.md](ROADMAP.md).

## Структура репозитория

```
vibefly/
├── apps/
│   ├── android/    — APK на Kotlin + Compose
│   ├── agent/      — Go-агент (живёт внутри Debian-rootfs)
│   └── cloud/      — Next.js cloud control plane (опционально)
│
├── runtime/
│   ├── rootfs-builder/      — скрипты сборки Debian-образа
│   └── droidspaces-notes/   — интеграция с Droidspaces
│
├── docs/           — vision, architecture, security, billing
└── .github/        — CI
```

## Лицензия

AGPL-3.0. См. [LICENSE](LICENSE).

Если нужна коммерческая лицензия без обязательств копилефта — пиши автору.

## Автор

[@igor1000rr](https://t.me/igor1000rr)
