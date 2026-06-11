# VibeFly — Roadmap до v1.0

> Обновлён 11.06.2026. Заменяет прежний план (фазы 0–5): учтены итоги майской сессии (HEAD `2f1689b`), отказ от Cloudflare Tunnel в пользу self-hosted Pangolin и решение по Droidspaces (exec-backend вместо JNI-моста или форка). Выполненное из старого плана перенесено в раздел «Статус», AI-фазы стали этапами 9–10.

## Продукт

**VibeFly** — phone-as-a-server PaaS: рутованный Android-телефон (Magisk/KernelSU) превращается в self-hosted сервер. Деплой приложений из GitHub-репозиториев по манифесту `vibefly.toml`, публичные URL, мониторинг и управление с телефона и из браузера.

**Целевой пользователь** — разработчик, который хочет хостить pet-проекты, Telegram-ботов и веб-сервисы на старом телефоне дома, без аренды VPS.

**MVP (v0.5.0):** пользователь вставляет URL репозитория → через минуту приложение запущено на телефоне и доступно по публичному адресу. Весь путь — из мобильного UI.

**v1.0:** MVP + изолированный runtime (Node/Python в контейнере), web dashboard, web-терминал, marketplace шаблонов, автостарт с загрузки телефона, self-update, подписанный APK и публичный релиз.

## Статус на 11.06.2026

Готово (фаза PoC + майская сессия):

- [x] Go-агент: REST API + WebSocket-логи, ExecSupervisor с auto-restart, persistence spec.json, автостарт приложений
- [x] Android-приложение: Kotlin + Compose, RuntimeManager (агент child-процессом, /health-поллинг, asset extraction), demo-mode без рута
- [x] auth_token: автогенерация 32 байта при первом старте, EncryptedSharedPreferences, Bearer на всех эндпоинтах кроме `/health`
- [x] Реальные метрики через root: battery/thermal из `/sys` (пакет rootx, fallback без рута)
- [x] Cgroup v2 лимиты per-app: `memory.max`, `cpu.max`, наследование дочерними процессами
- [x] Deploy-from-repo backend: `vibefly.toml` с GitHub, скачивание и распаковка `.tar.gz`/`.zip`, защита от path traversal и zip-bomb
- [x] Alpine 3.20 minirootfs бандлится в APK, идемпотентная фоновая распаковка на устройстве
- [x] CI: arm64-агент → сборка APK → артефакт; go vet/test, markdownlint
- [x] Cloudflare Tunnel — работает, под выпил в этапе 2

Не проверено на физическом устройстве: всё из майской сессии. Это закрывает этап 1.

## Принципы

1. **Graceful degradation.** Без root и без «правильного» ядра базовый сценарий (ExecSupervisor + бинарные приложения) обязан работать.
2. **Лицензионная гигиена.** GPL/AGPL-компоненты (Newt, Droidspaces) в APK не вшиваются: download-on-first-start и запуск отдельным процессом через exec.
3. **W^X.** Скачанные исполняемые файлы запускаются через `su -c` — Android 10+ запрещает exec из data-каталогов приложения.
4. **Публичный репозиторий.** Секреты, адреса серверов и детали чужой инфраструктуры в репо не попадают.
5. **Атомарные коммиты.** Каждый коммит самодостаточен, CI зелёный, безопасен к откату.
6. **Каждый этап заканчивается работающим результатом** с явным критерием готовности (DoD).

## Карта версий

| Версия | Этап | Содержание | Статус |
| --- | --- | --- | --- |
| v0.1.x | 0 | Решения и гигиена | в работе |
| v0.2.0 | 1 | Подтверждение фундамента на устройстве | — |
| v0.3.0 | 2 | Ingress: Cloudflare → Pangolin | — |
| v0.4.0 | 3 | Runtime v2: изоляция, реальные per-app метрики | — |
| v0.5.0 | 4 | Deploy-from-repo UI — **MVP** | — |
| v0.6.0 | 5 | Web dashboard | — |
| v0.7.0 | 6 | Web-терминал + marketplace | — |
| v0.8–0.9 | 7 | Продакшн-харднинг | — |
| v1.0 | 8 | Публичный релиз | — |
| v1.1 | 9 | AI-ассистент read-only | — |
| v1.2 | 10 | AI с действиями | — |

---

## Этап 0 — решения и гигиена (v0.1.x)

Цена: меньше одной сессии кода + ручные задачи.

- [ ] 0.1 [код] Вычистить из `docs/handoff-2026-05-14.md` инфраструктурные детали (адреса серверов, схему сети) — репозиторий публичный
- [ ] 0.2 [руки] `droidspaces check` на тест-устройстве: статический aarch64-бинарь из их releases, запуск через `su`. Результат выбирает ветку этапа 3 (3A или 3B)
- [ ] 0.3 [руки] Pangolin на собственном VPS рядом с существующим Traefik; DNS: A-запись и wildcard `*.vibefly.app`; создать site для телефона → получить `newt_id` / `newt_secret`
- [ ] 0.4 [руки] Гигиена публичного репо: включить GitHub secret scanning и Dependabot alerts

**DoD:** известна ветка runtime; админка Pangolin отвечает; в репозитории нет внутренней инфраструктурной информации.

## Этап 1 — фундамент на железе (v0.2.0, ~1 сессия + руки)

Чек-лист на свежем APK из CI:

- [ ] 1.1 Первый старт: в logcat генерация auth_token, API без Bearer отвечает 401
- [ ] 1.2 Распаковка rootfs: sentinel-файл на месте, `etc/alpine-release` = 3.20.3
- [ ] 1.3 Метрики: battery и thermal ненулевые после выдачи root (Magisk popup)
- [ ] 1.4 Cgroup-лимиты: каталог `/sys/fs/cgroup/vibefly-<id>` создаётся, `memory.max` применяется
- [ ] 1.5 `POST /apps/from-repo` с репозиторием, у которого в Releases есть arm64-тарбол (например Caddy) — деплой end-to-end
- [ ] 1.6 Перезапуск: kill агента → автостарт приложений из persistence, MinUptimeForRestart отрабатывает
- [ ] 1.7 W^X: если exec агента из filesDir заблокирован — перенос бинаря в jniLibs (`libagent.so`, nativeLibraryDir исполняемый) либо запуск через `su -c`; решение зафиксировать здесь
- [ ] 1.8 Багфикс-коммиты по результатам прогона

**DoD:** восемь майских фич подтверждены физическим устройством.

## Этап 2 — ingress: Cloudflare → Pangolin (v0.3.0, 1–2 сессии)

- [ ] 2.1 [код] Выпил cloudflared целиком: CI job `fetch-cloudflared`, Android assets, `tunnel/cloudflared.go`, `tunnel/app_tunnels.go`, UI Publish/PublicUrlCard. Чистый «минусовый» коммит
- [ ] 2.2 [код] `tunnel.PangolinManager` (реализует `tunnel.Manager`): download Newt on-first-start из официальных releases (AGPL — не бандлим), запуск через `su -c`, reconnect-loop с backoff, статус туннеля в `/health`. Конфиг: `pangolin_endpoint`, `newt_id`, `newt_secret` в `agent.toml`
- [ ] 2.3 [код] Android UI: Settings → Pangolin — три поля, кнопка «Подключиться», индикатор состояния туннеля
- [ ] 2.4 [код] Per-app публичные URL через Pangolin API (агент сам создаёт resources). Если API не позволяет — ручные resources в админке, автоматизация переезжает в 5.4

**DoD:** телефон из любой сети доступен по своему поддомену; задеплоенное приложение — по отдельному поддомену `*.vibefly.app`.

## Этап 3 — runtime v2 (v0.4.0, 1–2 сессии)

- [ ] 3.0 [код] Поле `AppSpec.Runtime` (`"" | "chroot" | "droidspaces"`) + фабрика супервизоров по нему

Ветка A — ядро прошло `droidspaces check`:

- [ ] 3A.1 `DroidspacesSupervisor`: download статического бинаря on-first-use, генерация `--conf` из AppSpec, контейнер-на-приложение
- [ ] 3A.2 Сеть NAT + проброс портов из манифеста (`--port`), статус-поллинг, graceful stop

Ветка B — ядро не прошло:

- [ ] 3B.1 `ChrootSupervisor`: mount `/proc`, `/sys`, `/dev`, `/dev/pts` + bind `resolv.conf`, chroot в rootfs-base, запуск `start_cmd`
- [ ] 3B.2 Teardown: lazy umount, защита от повторного mount, очистка при Uninstall

Общее:

- [ ] 3.1 [код] Реальные per-app метрики: `memory.current` + `cpu.stat` из cgroup → AppDetail (убрать хардкод 124/512)
- [ ] 3.2 [код] OverlayFS per-app поверх общей rootfs-base (для ветки B; в ветке A volatile-режим есть из коробки)
- [ ] 3.3 [код] Pre-start hooks из манифеста (`setup_cmd = "apk add nodejs npm"`) с кешированием результата

**DoD:** манифест с `runtime = "chroot"` и `start_cmd = "node server.js"` работает end-to-end на устройстве.

## Этап 4 — deploy-from-repo UI = MVP (v0.5.0, ~1 сессия)

- [ ] 4.1 Поле «GitHub URL» в +Deploy → `GET /manifest/preview` → карточка превью: имя, порт, лимиты, выбор runtime
- [ ] 4.2 Install с прогресс-стейтами (download → extract → start) и человекочитаемыми ошибками манифеста
- [ ] 4.3 Smoke-прогон: три разных публичных репозитория с `vibefly.toml`

**DoD:** «вставил URL → через минуту приложение на публичном адресе» целиком из UI. Точка для демо и первых бета-тестеров.

## Этап 5 — web dashboard (v0.6.0, ~2 сессии)

- [ ] 5.1 Embedded SPA в Go-агенте через `embed.FS`; предсобранный бандл коммитится в репо (Node вне критического пути CI)
- [ ] 5.2 Экраны: список приложений со статусами, live-метрики, WebSocket-стрим логов, deploy-форма
- [ ] 5.3 Auth: тот же Bearer + страница входа; доступ через админский туннель
- [ ] 5.4 Автоматизация Pangolin resources, если не закрыта в 2.4

**DoD:** полное управление телефоном из браузера; APK нужен только для первичной настройки.

## Этап 6 — web-терминал + marketplace (v0.7.0, 1–2 сессии)

- [ ] 6.1 xterm.js + WebSocket → PTY-shell внутрь контейнера приложения и в rootfs-base
- [ ] 6.2 Marketplace: готовые `vibefly.toml` + собранные нами arm64-артефакты — Node hello-world, Flask, Redis, PostgreSQL
- [ ] 6.3 Debian rootfs.img как опциональный «тяжёлый» образ (наработки PoC-фазы) для стеков, которым мало Alpine

**DoD:** шаблон из marketplace деплоится в два тапа; в контейнер можно зайти терминалом из браузера.

## Этап 7 — продакшн-харднинг (v0.8–0.9, 2–3 сессии)

Надёжность:

- [ ] 7.1 Watchdog агента + автостарт через Magisk `service.d`: переживает ребут без открытия приложения; учесть FBE — конфиг в device-protected storage либо отложенный старт после unlock
- [ ] 7.2 Wi-Fi lock, исключение из battery optimization, обработка смены сети (reconnect Newt)
- [ ] 7.3 Прогон на парке устройств (Pixel / S10 / OneTouch / Redmi)

Phone-care — телефон 24/7 в розетке:

- [ ] 7.4 Ограничение заряда 60–80% через su (`charge_control_limit`), если прошивка поддерживает
- [ ] 7.5 Thermal-троттлинг: динамическое снижение `cpu.max` приложениям при перегреве
- [ ] 7.6 Алерты в Telegram: офлайн, перегрев, падения приложений

Безопасность:

- [ ] 7.7 Ротация auth_token из UI; rate-limit на публичных эндпоинтах
- [ ] 7.8 Шифрованное хранение `[env]`-секретов манифеста; отказ деплоить манифест без лимитов ресурсов
- [ ] 7.9 Audit-log действий

Обновления и качество:

- [ ] 7.10 Self-update агента: download → swap → restart с откатом при провале
- [ ] 7.11 Проверка новых версий APK через GitHub Releases
- [ ] 7.12 Тесты: unit (manifest, binstore, supervisor), e2e smoke через adb, golangci-lint в CI

**DoD:** телефон месяц живёт без рук — переживает ребут, потерю сети и перегрев, обновляется сам.

## Этап 8 — публичный релиз v1.0 (1–2 сессии + руки)

- [ ] 8.1 [руки] Ключ подписи release-APK, secrets в GitHub Actions
- [ ] 8.2 [код] Release-workflow: signed APK + changelog по тегу
- [ ] 8.3 [код] Onboarding-мастер: root-check → подключение Pangolin → первый деплой за 5 минут
- [ ] 8.4 [код] Документация: Quickstart RU/EN, справочник `vibefly.toml`, troubleshooting
- [ ] 8.5 [руки] README EN, лендинг vibefly.app, F-Droid metadata (GPL-кода в APK нет — проходим), посты Reddit / HN / Habr

**DoD:** незнакомый человек с рутованным телефоном доходит до работающего публичного приложения по Quickstart без нашей помощи.

## Этап 9 — AI-ассистент read-only (v1.1)

- [ ] 9.1 AI Router на отдельном воркере: multi-provider (DeepSeek, Groq и другие), free tier на дешёвых моделях
- [ ] 9.2 ProxyAiClient заменяет StubAiClient в приложении
- [ ] 9.3 Read-only tools: `list_apps`, `get_logs`, `system_metrics` (ToolRegistry уже в коде)
- [ ] 9.4 Сценарии: «почему упало приложение X», «что ест память»

## Этап 10 — AI с действиями (v1.2)

- [ ] 10.1 Write-tools с approval flow: `restart_app`, `stop_app`
- [ ] 10.2 Расширение: `deploy`, `env_change`, `db_migrate`
- [ ] 10.3 Audit log AI-действий + security review tool definitions

---

## Критический путь

**0 → 1 → 2 → 4 = MVP за ~4–5 сессий.** Этап 3 сознательно вне MVP: бинарные приложения (Go, Caddy, статика) уже работают на ExecSupervisor, Node/Python догоняют в v0.4. Если Pangolin задерживается на ручной стороне — этапы 2 и 3 меняются местами без потерь. Полный путь до v1.0 — ориентировочно 10–14 сессий.

## Риски

| Риск | Влияние | Митигация |
| --- | --- | --- |
| Зоопарк ядер: нет нужных CONFIG_* | Ветка 3A недоступна | Ветка 3B (свой chroot) работает на любом ядре; проверка в 0.2 до начала кода |
| Агрессивные OEM-киллеры (MIUI и подобные) | Агент умирает в фоне | Foreground service + watchdog + `service.d`-автостарт (7.1–7.2) |
| W^X на Android 10+ | Скачанные бинари не запускаются | Запуск только через `su -c`; для агента — jniLibs (1.7) |
| Pangolin API не даёт создавать resources программно | Нет автоматических per-app URL | Ручные resources в админке; автоматизация в 5.4 |
| AGPL (Newt) / GPL (Droidspaces) | Лицензионное заражение APK | Только download-on-first-start + exec; в APK не вшиваем |
| Один основной dev-девайс | Слепые зоны совместимости | Парк устройств с этапа 7; бета-тестеры с этапа 4 |
| FBE: `/data` зашифрован до unlock | Автостарт с загрузки ломается | Device-protected storage или отложенный старт (7.1) |

## Зафиксированные решения

| Когда | Решение | Причина |
| --- | --- | --- |
| 05.2026 | Cloudflare Tunnel выпиливаем | Требование независимости ingress от Cloudflare |
| 06.2026 | Ingress = self-hosted Pangolin на своём VPS | Полный контроль, ноль новых расходов, Traefik и сертификаты уже есть |
| 06.2026 | Droidspaces: не форкаем, не вшиваем, без JNI | Kernel-требования форк не лечит; GPL остаётся GPL; exec внешнего бинаря закрывает ~95% потребностей; свой chroot — fallback |
| 05.2026 | Rootfs по умолчанию = Alpine minirootfs (~3 MB в APK) | Размер APK; Debian .img — опция для тяжёлых стеков (6.3) |
| — | arm64-only, minSdk 26, root (Magisk/KernelSU) ожидается | Phone-as-a-server без root и на x86 смысла не имеет |

## Вне скоупа v1.0 (бэклог)

- Hosted ingress / cloud control plane (раздача поддоменов пользователям) — вместе с биллингом, см. `docs/06-billing-model.md`
- P2P/mesh-доступ (Headscale/Tailscale) как альтернатива туннелю
- Droidspaces как полноценный второй runtime у конечных пользователей (после обкатки на dev-стенде)
- Мульти-девайс fleet management
- iOS — никогда (нет root)

## Связанные документы

- `docs/repo-deploy.md` — формат `vibefly.toml` и deploy-from-repo
- `docs/phase2-chroot.md` — историческая детализация runtime-фазы (поглощена этапом 3)
- `docs/06-billing-model.md` — гипотеза монетизации
