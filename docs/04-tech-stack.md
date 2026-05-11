# Tech stack

## Обоснование выбора

### Kotlin + Jetpack Compose для UI

Альтернативы:

- **React Native** — кросс-платформенность, но мы Android-only by design (iOS не позволит namespace-runtime). Лишний overhead.
- **Flutter** — то же самое.
- **Java + XML** — устаревший подход, медленнее разработка.
- **PWA в WebView** — упрощение, но требует постоянной работы агента и нет нативной интеграции с Foreground Service / BootReceiver.

Kotlin + Compose — нативный, быстрый, единственный правильный выбор для Android-only приложения с системной интеграцией.

### Go для агента

Альтернативы:

- **Node.js / TypeScript** — нужен Node-runtime в rootfs (+80 МБ), запуск медленнее, больше памяти.
- **Rust** — отлично, но кривая обучения и медленнее писать.
- **Python** — медленный, тяжёлый runtime.
- **C / C++** — слишком низкоуровнево для бизнес-логики.

Go: один статичный бинарник без зависимостей, отличная concurrency-модель для WebSocket-стримов логов, быстрая компиляция, ~10 МБ финального бинарника.

### Next.js для cloud control plane

Если вообще делаем cloud-режим — Next.js 15 + Prisma + PostgreSQL + NextAuth. Стандарт, у автора большой опыт (azcrm, crm-platform). Хостится на Coolify.

### Debian 12 для rootfs

Альтернативы:

- **Alpine** — меньше (50 МБ), но musl ломает половину npm-нативных модулей (sharp, puppeteer, prisma).
- **Ubuntu 24.04** — почти то же что Debian, чуть тяжелее, snap-засорение.
- **Arch** — слишком rolling для embedded.

Debian — стабильность, glibc-совместимость, минимальный (с --variant=minbase) разумного размера.

### Droidspaces для namespace-runtime

Единственная зрелая alternative chroot/proot для Android в 2026, поддерживает systemd как PID 1, единый статичный бинарник 150 КБ. Лицензия GPL v3 — заразная, что совместимо с нашим AGPL-3.0 ядром, но потребует осторожности при коммерческой версии (либо договариваться, либо писать свой namespace-launcher на ~2K строк C).

## Языковая политика

- **Идентификаторы кода (variables, functions, types):** английский
- **Комментарии:** русский
- **README, docs, ARCHITECTURE, ROADMAP:** русский
- **Commit messages:** русский в формате Conventional Commits
- **Issues / PRs (после публичного релиза):** английский (по согласованию с автором — можно русский)
- **UI-строки:** мультиязычные с дефолтом RU/EN

## Версии зависимостей (фиксируются по ходу разработки)

- Kotlin 2.0+
- Compose 1.7+
- Android Gradle Plugin 8.7+
- Go 1.22+
- Node 22 (в rootfs)
- Python 3.12 (в rootfs)
- Debian 12 bookworm (rootfs base)
- PostgreSQL 16 (опционально в rootfs)
