# VibeFly — онбординг для соучредителя

Один файл, чтобы быстро понять *что* собрано, *где* лежит, *как* запустить. Для глубоких деталей — ссылки в конце.

## TL;DR

Phone-as-a-Server PaaS. Android-приложение превращает рутованный смартфон в саморазмещаемый сервер для пет-проектов и AI-агентов. Управление через chat с AI-ассистентом — как Lovable/Bolt, но AI работает с **живым сервером**, а не генерирует статичные сайты.

- **Open-source ядро** (AGPL-3.0) — APK + Go-агент + Debian rootfs
- **Бесплатно для пользователей.** Тарифы и монетизация — отложены, не ранее чем продукт стабилизируется и наберёт аудиторию. План остаётся в `docs/06-billing-model.md` как будущая гипотеза.
- **Целевое железо** — тестовый парк: Alcatel OneTouch, Google Pixel, Samsung Galaxy S10, Redmi (включая Note 14S). Минимальные требования: Android 10+, ARM64, root, 4 ГБ RAM.

## Что собрано

- UI всех 5 экранов в скевоморфизме iOS 6 на Jetpack Compose: Dashboard, App Detail, Marketplace, Vibe AI chat, Settings.
- Go-агент с REST API и WebSocket-стримом логов, supervisor поверх systemd, marketplace из 10 шаблонов (Vaultwarden, n8n, Uptime Kuma, Pi-hole, Memos и т.д.).
- Builder Debian 12 ARM64 rootfs — CI собирает компактный образ `.img.zst` ~280 MB на каждый push.
- Demo-mode: APK работает без агента и без рута, отдаёт реалистичные данные для скриншотов и презентаций.
- ToolRegistry: AI-инструменты `list_apps`, `get_logs`, `system_metrics`, `restart`, `stop` с двухуровневой моделью approval (опасные действия требуют тапа пользователя).
- CI workflows: `lint-docs`, `build-agent`, `build-rootfs` — зелёные. Артефакты (агент arm64, rootfs образ) доступны прямо со страницы Actions.

## Целевые устройства

Парк тестирования (приоритет — сверху вниз):

| Устройство | SoC | Android | Зачем в парке |
|---|---|---|---|
| Redmi Note 14S | MediaTek Helio G99 / Dimensity | 14 | основное dev-устройство |
| Google Pixel (3a / 4a / 6) | Tensor / Snapdragon 670 | 11–14 | эталонный AOSP, проще debugging |
| Samsung Galaxy S10 | Exynos 9820 / Snapdragon 855 | 12 (custom ROM) | проверка работы на Exynos |
| Alcatel OneTouch | бюджетный MediaTek/Snapdragon | 10–11 | проверка на нижней планке CPU |

Конкретные модели можем менять — главное чтобы парк покрывал три измерения: топовый/средний/слабый, MediaTek/Snapdragon/Exynos, чистый AOSP/MIUI/OneUI.

**Минимальные требования для пользователя:**
- Android 10+ (для namespace API)
- ARM64 (`arm64-v8a`, не armeabi-v7a)
- root (Magisk/KernelSU предпочтительнее SuperSU)
- ≥ 4 ГБ RAM, ≥ 4 ГБ свободного места под rootfs

## Структура репо

```
apps/
  android/              Kotlin + Compose, package by.vibefly.app
    app/src/main/java/by/vibefly/app/
      agent/            AgentApi + AgentClient + MockAgentClient + AiClient
      data/             Repositories, SettingsStore, ToolRegistry, ServiceLocator
      ui/
        screens/        Dashboard, AppDetail, Marketplace, Chat, Settings (+ ViewModels)
        components/     Skeu* (NavBar, GroupedTable, IosToggle, PhosphorIcon, ApprovalCard…)
        theme/          палитра + градиенты iOS 6
      service/          Foreground Service для namespace-runtime
      runtime/          JNI bridge к Droidspaces
  agent/                Go 1.22, REST API внутри rootfs как systemd-service
    cmd/agent/main.go   entrypoint
    internal/
      server/           chi router + WebSocket + Bearer auth
      supervisor/       systemd-обёртка (Install/Start/Stop/FollowLogs)
      marketplace/      каталог 10 шаблонов (Vaultwarden, n8n, Uptime Kuma…)
      metrics/          чтение /proc, /sys/class/power_supply
      logs/             ring-buffer pub/sub
      apps/             менеджер пользовательских приложений
    docs/API.md         REST спека v0.3
runtime/
  rootfs-builder/       debootstrap → ext4 → zstd
  droidspaces-notes/    как мы используем https://github.com/ravindu644/Droidspaces-OSS
docs/                   техдокументация (vision/architecture/security/competitors)
.github/workflows/
  ci.yml                lint-docs + build-agent + build-android
  rootfs.yml            сборка arm64 rootfs.img.zst
```

## Как запустить

### Агент локально (без rootfs, на хост-системе)

```bash
cd apps/agent
go run ./cmd/agent
# проверка:
curl http://127.0.0.1:3001/health
```

В non-Linux окружении `NopSupervisor` подменит systemd — этого достаточно чтобы потыкать API.

### Android — самый быстрый путь (Demo mode)

```bash
cd apps/android
./gradlew :app:assembleDebug
```

После установки APK: **Settings → Connections → Demo mode ON**. Весь UI работает на `MockAgentClient`, который держит in-memory 4 приложения и 5 marketplace-шаблонов. Никаких сетевых вызовов.

### Android против реального агента

1. Запустить агент локально или на сервере (см. выше).
2. Settings → Demo mode OFF.
3. Settings → Agent URL → `http://<host>:3001` (для эмулятора Android — `http://10.0.2.2:3001`).
4. Settings → Auth token (если в `agent.toml` задан `auth_token`).

### Rootfs

CI собирает образ автоматически. Готовый artifact: Actions → rootfs → последний run → `vibefly-rootfs-arm64`. Локально debootstrap требует Linux + sudo + qemu-user-static.

## Ключевые точки в коде

| Хочу понять… | Смотри файл |
|---|---|
| Как UI говорит с агентом | `apps/android/.../agent/AgentApi.kt` + `AgentClient.kt` |
| Какие данные хранятся локально | `apps/android/.../data/SettingsStore.kt` (EncryptedSharedPreferences) |
| Где список AI-инструментов | `apps/android/.../data/ToolRegistry.kt` |
| Как чат обрабатывает tool-calls | `apps/android/.../ui/screens/ChatViewModel.kt` |
| Скевоморфные компоненты | `apps/android/.../ui/components/SkeuComponents.kt` |
| HTTP API агента | `apps/agent/internal/server/router.go` + `docs/API.md` |
| Шаблоны marketplace | `apps/agent/internal/marketplace/catalog.go` |
| Установка приложений на устройстве | `apps/agent/internal/supervisor/supervisor.go` |
| Что попадает в rootfs | `runtime/rootfs-builder/packages.txt` + `build.sh` |

## Конвенции, которые мы держим

- Commits / комментарии / README — **на русском**. Идентификаторы в коде — английские.
- Push идёт прямо в `main` через GitHub MCP. Это упрощено пока команда = 1-2 человека.
- **GitHub-токены никогда не вставляются в чат и не пушатся из контейнера.** Все commit'ы — либо через MCP, либо локально.
- Тесты прогоняем в конце цикла разработки, не после каждого патча.

## Roadmap

| Фаза | Что | Срок |
|---|---|---|
| 0 — PoC | агент + базовый UI | май 2026 ✓ |
| 1 — личный dogfooding | rootfs + Marketplace + Demo mode + тест на 2-3 устройствах парка | май 2026 |
| 2 — embedded APK | rootfs внутри APK, тест на всём парке (Pixel / S10 / OneTouch / Redmi) | июнь 2026 |
| 3 — public launch | публичный релиз, README на en | июль 2026 |
| 4 — AI read-only | расширенный AiClient + tool-calling через Cloudflare Worker | август 2026 |
| 5 — AI с действиями | расширенный approval-flow, опасные операции (миграции, удаления) | осень 2026 |

Подробно: `ROADMAP.md`.

## Дальше читать

- `README.md` — что-зачем для внешнего читателя
- `ARCHITECTURE.md` — три слоя (UI / Agent / Runtime) и как они общаются
- `docs/01-architecture.md` — техдетали (Droidspaces, namespace-runtime, JNI)
- `docs/04-tech-stack.md` — обоснование выбора Kotlin / Go / Debian
- `docs/05-security-model.md` — threat model + защита от prompt injection
- `docs/06-billing-model.md` — будущая гипотеза монетизации (сейчас не применяется, продукт бесплатный)
- `docs/07-competitors.md` — vs Bolt / Lovable / v0 / Coolify / Vercel / Andronix
- `apps/agent/docs/API.md` — REST API v0.3

## Связь

- Owner: @igor1000rr · [t.me/igor1000rr](https://t.me/igor1000rr)
- Лицензия: AGPL-3.0 (см. `LICENSE`)
- Связано с Droidspaces OSS (GPL-3.0): https://github.com/ravindu644/Droidspaces-OSS
