# Архитектура: подробно

## Слой 1 — UI (Android, Kotlin + Compose)

### Стек

- Kotlin 2.0+
- Jetpack Compose с Material 3
- Coroutines + Flow для асинхронности
- Ktor Client для HTTP к локальному агенту
- DataStore для настроек
- EncryptedSharedPreferences для секретов

### Ключевые компоненты

**MainActivity** — точка входа, host для Compose-навигации.

**Navigation** — Navigation Compose, 5 основных destinations:

- `dashboard` — главный экран
- `app_detail/{id}` — детали приложения
- `chat` — AI-ассистент
- `marketplace` — каталог one-click
- `settings` — настройки

**VibeflyService** — Foreground Service, держит namespace-runtime в живом состоянии, не даёт Android'у убить agent.

**RuntimeManager** — Kotlin-обёртка через JNI над namespace-runtime (Droidspaces). Запуск, остановка, проверка статуса rootfs.

**AgentClient** — HTTP-клиент к `http://127.0.0.1:3001`.

**BootReceiver** — `BOOT_COMPLETED` → запуск VibeflyService.

### Дизайн

Мокапы в `docs/03-ui-mockups.md`. Дизайн-язык — **функциональный mobile, не скевоморфизм** для продакшн-версии (скевоморфизм iOS 6 был экспериментом).

Цветовая палитра — production будет определена ближе к фазе 2.

## Слой 2 — Agent (Go)

### Стек

- Go 1.22+
- Chi router (или Echo)
- nhooyr.io/websocket для WS
- spf13/cobra для CLI-флагов
- sqlx + SQLite (локальный state)

### Структура

```
apps/agent/
├── cmd/
│   └── agent/
│       └── main.go              — точка входа
├── internal/
│   ├── server/                  — HTTP/WS сервер
│   ├── apps/                    — управление приложениями (pm2-like)
│   ├── metrics/                 — battery, temp, cpu, ram
│   ├── deploy/                  — git clone, build, install
│   ├── tunnel/                  — обёртка над cloudflared
│   ├── ai/                      — AI tools execution с verification
│   └── auth/                    — token-based auth с UI
├── pkg/                         — переиспользуемое
└── go.mod
```

### Процесс-менеджер

Для управления пользовательскими приложениями — **встроенный supervisor** вместо pm2. Причины:

- один статичный бинарник без зависимостей от Node
- лучшая интеграция с systemd через journald
- быстрее, меньше памяти

Каждое user-app — это systemd unit, сгенерированный агентом в `/etc/systemd/system/vibefly-app-{id}.service`.

## Слой 3 — Runtime (Debian rootfs)

### Сборка rootfs.img

Через `debootstrap` + chroot scripting:

```bash
debootstrap --arch=arm64 --variant=minbase bookworm \
  out/rootfs http://deb.debian.org/debian/

chroot out/rootfs apt install -y \
  systemd nodejs python3 git nginx \
  cloudflared tailscale

# наш агент
cp build/vibefly-agent out/rootfs/usr/local/bin/
cp config/vibefly-agent.service out/rootfs/etc/systemd/system/

# упаковка в ext4 image
mkfs.ext4 -d out/rootfs out/rootfs.img
```

### Размер

- Минимальный Debian: 200 МБ
- + node 22: +80 МБ
- + python 3.12: +60 МБ
- + nginx, postgresql, redis: +150 МБ
- + tooling (git, build-essential): +120 МБ
- + cloudflared, tailscale: +50 МБ
- **Итого:** ~700 МБ несжатый, ~280 МБ сжатый

### Поставка

rootfs.img слишком большой для прямого включения в APK (Google Play limit 200 МБ APK + 4 ГБ Asset Pack). Решения:

1. **Asset Pack delivery** через Play Asset Delivery — пользователь скачивает APK 10 МБ + rootfs скачивается отдельно
2. **Скачивание с CDN при первом запуске** — APK 10 МБ, при старте скачивает rootfs из GitHub Releases или Cloudflare R2
3. **Сборка rootfs на устройстве** — минимальный bootstrap прямо на телефоне (медленно, не рекомендуется)

Рабочий вариант на старте — №2 (CDN), позже №1 для Play Store.

## Слой 0 — Namespace-runtime

### Droidspaces (основной выбор)

[github.com/ravindu644/Droidspaces-OSS](https://github.com/ravindu644/Droidspaces-OSS) — single static binary 150 КБ. Запускает Debian-rootfs с systemd как PID 1 через Linux namespaces.

**Требования:**

- Ядро с включёнными `CONFIG_USER_NS`, `CONFIG_PID_NS`, `CONFIG_MOUNT_NS`, `CONFIG_NET_NS`, `CONFIG_IPC_NS`, `CONFIG_UTS_NS`
- Cgroups v2 (рекомендуется) или v1
- Поддержка `pivot_root`, OverlayFS
- Root доступ (KernelSU-Next предпочтительно; Magisk — экспериментально)

**Совместимость в нашем кейсе:** проверяется встроенным Requirements-чекером в Droidspaces APK на конкретном устройстве.

### Fallback — собственный proot-wrapper

Если Droidspaces не запускается (старое ядро, отключённые namespaces), используем proot. Минусы: нет настоящей изоляции, systemd работает в degraded режиме, общий PID-tree с Android. Плюсы: работает почти везде без root.

Решение принимается **per-device** при первом запуске APK на основе автодиагностики.
