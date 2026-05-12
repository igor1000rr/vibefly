# VibeFly — онбординг для соучредителя

Один файл, чтобы быстро понять *что* собрано, *где* лежит, *как* запустить. Для глубоких деталей — ссылки в конце.

## TL;DR

Phone-as-a-Server PaaS. Android-приложение превращает рутованный смартфон в саморазмещаемый сервер для пет-проектов и AI-агентов. Управление через chat с AI-ассистентом — как Lovable/Bolt, но AI работает с **живым сервером**, а не генерирует статичные сайты.

- **Open-source ядро** (AGPL-3.0) — APK + Go-агент + Debian rootfs
- **Cloud control plane** (будущее, фаза 5) — биллинг, тарифы Free / Pro $9 / Studio $29 / BYOK $5
- **Целевое железо** — Redmi Note 14S с root, в перспективе любой Android 10+ ARM64

## Где мы сегодня (12 мая 2026)

**Готово:**
- UI всех 5 экранов в скевоморфизме iOS 6 (Compose)
- Go-агент: REST + WebSocket, supervisor поверх systemd, marketplace из 10 шаблонов
- Builder Debian 12 ARM64 rootfs (CI собирает `.img.zst` ~280 MB)
- Demo-mode: APK работает без агента, фейковые данные для скриншотов
- ToolRegistry: 5 AI-инструментов (list_apps, get_logs, system_metrics, restart, stop) с approval-флагами
- CI: lint-docs / build-agent / build-rootfs — зелёные

**Не готово:**
- `gradle wrapper` — нужно один раз сгенерить локально (10 мин)
- JNI bridge к Droidspaces — ждём скрин Requirements с Note 14S
- Упаковка rootfs в APK — пока через CDN download (план: Android Asset Pack)
- Реальный AI-клиент — сейчас `StubAiClient` echo'ит, нужен `CloudflareProxyAiClient` (фаза 4)
- Биллинг, sign APK, F-Droid — фаза 5

Полная история по фазам: `ROADMAP.md`.

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
      runtime/          JNI bridge к Droidspaces (заглушка)
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
docs/                   техдокументация (vision/architecture/security/billing/competitors)
.github/workflows/
  ci.yml                lint-docs + build-agent + (build-android, скип без wrapper)
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

В non-Linux окружении `NopSupervisor` подменит systemd, marketplace-install будет no-op. Этого достаточно чтобы потыкать API.

### Android — самый быстрый путь (Demo mode)

```bash
cd apps/android
gradle wrapper --gradle-version 8.10.2    # один раз, сохрани результат в git
./gradlew :app:assembleDebug
```

После установки APK: **Settings → Connections → Demo mode ON**. Весь UI работает на `MockAgentClient`, который держит in-memory 4 приложения и 5 marketplace-шаблонов. Никаких сетевых вызовов.

### Android против реального агента

1. Запустить агент локально или на сервере (см. выше)
2. Settings → Demo mode OFF
3. Settings → Agent URL → `http://<host>:3001` (для эмулятора Android — `http://10.0.2.2:3001`)
4. Settings → Auth token (если в `agent.toml` задан `auth_token`)

### Rootfs (только в CI)

Локально debootstrap требует Linux + sudo + qemu-user-static. Проще брать готовый artifact из GitHub Actions: Actions → rootfs → последний run → `vibefly-rootfs-arm64`.

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
- Push идёт прямо в `main` через GitHub MCP (без feature-веток, без PR). Это упрощено пока команда = 1-2 человека.
- **GitHub-токены никогда не вставляются в чат и не пушатся из контейнера.** Если MCP недоступен — я выдаю готовые git-команды для локального запуска.
- Тесты прогоняем в конце цикла разработки, не после каждого патча.
- Файлы создаваемые через MCP не имеют executable bit — для shell-скриптов либо `chmod +x` в CI, либо запуск через `bash script.sh`.

## Roadmap в одной таблице

| Фаза | Что | Срок |
|---|---|---|
| 0 — PoC | агент + базовый UI | ✅ готово |
| 1 — личный dogfooding | rootfs + Marketplace + Demo mode | 🟡 закрываем (gradle wrapper, JNI bridge) |
| 2 — embedded APK | rootfs внутри APK, sign, F-Droid | май-июнь 2026 |
| 3 — open-source launch | публичный релиз, README на en | июль 2026 |
| 4 — AI read-only | реальный AiClient + tool-calling | август 2026 |
| 5 — AI с действиями + биллинг | approval-flow, Cloud control plane, Pro/Studio | осень 2026 |

Подробно: `ROADMAP.md`.

## Дальше читать

- `README.md` — что-зачем для внешнего читателя
- `ARCHITECTURE.md` — три слоя (UI / Agent / Runtime) и как они общаются
- `docs/01-architecture.md` — техдетали (Droidspaces, namespace-runtime, JNI)
- `docs/04-tech-stack.md` — обоснование выбора Kotlin / Go / Debian
- `docs/05-security-model.md` — threat model + защита от prompt injection
- `docs/06-billing-model.md` — экономика тарифов, MRR-сценарии $5–15k за 12 месяцев
- `docs/07-competitors.md` — vs Bolt / Lovable / v0 / Coolify / Vercel / Andronix
- `apps/agent/docs/API.md` — REST API v0.3

## Связь

- Owner: @igor1000rr · [t.me/igor1000rr](https://t.me/igor1000rr)
- Лицензия: AGPL-3.0 (см. `LICENSE`)
- Связано с Droidspaces OSS (GPL-3.0): https://github.com/ravindu644/Droidspaces-OSS
