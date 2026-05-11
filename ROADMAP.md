# Roadmap

План реализации VibeFly по фазам. Каждая фаза заканчивается чем-то рабочим.

## Фаза 0 — Proof of Concept (2 недели)

**Цель:** доказать, что embedded Linux реально работает на современных Android, и что наш PaaS-подход жизнеспособен.

### Чек-лист

- [ ] Установить Droidspaces APK на тестовое устройство (Redmi Note 14S с root)
- [ ] Запустить Requirements check, документировать какие kernel-features доступны
- [ ] Если всё ок — собрать минимальный Debian rootfs.img вручную через debootstrap
- [ ] Запустить Debian в Droidspaces с systemd
- [ ] Внутри Debian поднять nginx, отдать статичную страницу
- [ ] Проверить что страница доступна по локальному IP с другого устройства в Wi-Fi
- [ ] Настроить Cloudflare Tunnel, опубликовать на поддомен
- [ ] 24-часовой стресс-тест: nginx под нагрузкой, метрики каждый час
- [ ] Документировать результаты в `docs/poc-results.md`

**Критерий успеха:** Debian с systemd работал 24+ часа без падений, температура не превышала 50°C под постоянной нагрузкой, страница доступна снаружи через Cloudflare Tunnel.

**Сценарий провала:** Droidspaces не запускается / падает / Wi-Fi отваливается / троттлинг убивает производительность. В этом случае — переходим на fallback path (proot) и пересматриваем стек.

---

## Фаза 1 — Личная dogfooding-версия (4 недели)

**Цель:** VibeFly работает на одном телефоне для одного пользователя (нас). Никаких APK, никакого UI — командная строка. Цель — понять подводные камни.

### Чек-лист

- [ ] Написать минимальный Go-агент с REST API:
  - `GET /health`
  - `GET /apps`
  - `POST /apps/:id/restart`
  - `GET /apps/:id/logs`
- [ ] Установить агент в Debian как systemd-сервис
- [ ] Написать клиент на curl-скриптах для проверки API
- [ ] Расширить агента:
  - `POST /apps` — создать из git URL
  - `DELETE /apps/:id` — удалить
  - `GET/PUT /apps/:id/env` — env vars
  - `POST /apps/:id/deploy` — git pull + build + restart
- [ ] WebSocket-эндпоинт `/apps/:id/logs/stream` для лайв-логов
- [ ] Метрики устройства: `/sys/class/power_supply/`, `/proc/meminfo`, `/proc/loadavg`
- [ ] Перенести один реальный сервис на телефон (мелкий telegram-бот)
- [ ] Жить с этим неделю, фиксировать баги

**Критерий успеха:** один реальный сервис живёт на телефоне 7+ дней без ручного вмешательства, метрики собираются, API работает стабильно.

---

## Фаза 2 — Первая версия Android-приложения (6 недель)

**Цель:** убрать терминал, заменить нативным UI.

### Чек-лист

- [ ] Создать Android-проект (Kotlin, Compose, min SDK 28)
- [ ] Foreground Service для удержания runtime в фоне
- [ ] JNI / shell wrapper для Droidspaces (start/stop/exec)
- [ ] Загрузка rootfs.img из assets при первом запуске
- [ ] HTTP/WS клиент к localhost:7070
- [ ] Главный экран (Dashboard) по нашим мокапам:
  - блок Device health (battery, temp, cpu, ram)
  - список приложений с статусом
  - bottom navigation
- [ ] Экран App Detail:
  - вкладки Overview / Logs / Env / Deploys
  - кнопки Restart / Stop / Redeploy
  - лайв-логи через WebSocket
- [ ] Экран Create App с git URL и формой env
- [ ] Notification из Foreground Service: "VibeFly running · 3 apps · 38°C"
- [ ] Onboarding flow: разрешения, запуск runtime, привязка к Cloudflare (опционально)
- [ ] Авто-генерация Bearer-токена для агента через Android Keystore

**Критерий успеха:** на любом Android-телефоне (root желателен, но не обязателен) пользователь устанавливает APK и за 10 минут получает работающий PaaS с одним приложением. Без открытия терминала.

---

## Фаза 3 — Marketplace + Tunnel автоматизация (4 недели)

**Цель:** убрать ручную работу с доменами и one-click установка популярных приложений.

### Чек-лист

- [ ] Marketplace-каталог как JSON-манифесты в репо
  - каждый манифест: имя, описание, git, env-шаблон, health-check, ресурсы
- [ ] Первые 10 приложений:
  - Vaultwarden (менеджер паролей)
  - Uptime Kuma
  - n8n
  - Memos
  - PostgreSQL + pgAdmin
  - AdGuard Home
  - Static blog (Astro)
  - Telegram-бот шаблон (grammy)
  - HTTP API шаблон (Express)
  - Webhook receiver
- [ ] UI marketplace по нашим мокапам
- [ ] One-click install: тап → форма env → deploy
- [ ] Cloudflare Tunnel автоматизация:
  - OAuth flow с Cloudflare в UI
  - Создание туннеля, проброс поддоменов автоматом
  - Свой домен `*.vibefly.dev` для пользователей без своего домена
- [ ] Backup/restore приложений через UI (tar.gz в Cloudflare R2)

**Критерий успеха:** новый пользователь может за 15 минут установить APK → выбрать Vaultwarden → получить рабочий менеджер паролей на собственном домене.

---

## Фаза 4 — AI ассистент Read-only (6 недель)

**Цель:** AI смотрит и объясняет. Никаких действий.

### Чек-лист

- [ ] AI Router как Cloudflare Worker:
  - принимает запросы от APK
  - роутит к Anthropic / OpenAI / OpenRouter
  - считает токены, ведёт лимиты
  - BYOK режим (свой ключ пользователя)
- [ ] Tool API в Agent (read-only):
  - `get_metrics(timeframe)`
  - `get_logs(app, timeframe, filter)`
  - `list_apps()`
  - `get_app_config(app)`
  - `get_deploy_history(app)`
  - `explain_error(log_excerpt)`
- [ ] Чат-экран в UI:
  - сообщения и tool calls (рендерим как в наших мокапах)
  - индикатор thinking, печатает
  - история сохраняется локально
- [ ] System prompts: характер ассистента, безопасное поведение, ограничения
- [ ] Тестирование на 50+ реальных сценариях

**Критерий успеха:** пользователь спрашивает "почему amina-bot потребляет 400 МБ RAM" — AI смотрит логи, метрики, объясняет понятным языком. В 80% случаев полезный ответ.

---

## Фаза 5 — AI с действиями + биллинг (10 недель)

**Цель:** платный продукт с AI, который реально работает.

### Чек-лист

- [ ] Write-tools с approval flow:
  - `suggest_restart(app)` → UI Approve кнопка
  - `suggest_env_change(app, key, value)` → diff + Approve
  - `suggest_db_migration(sql)` → preview + Approve
  - `suggest_deploy(app, branch)` → confirm + Approve
  - `suggest_create_app(spec)` → preview + Approve
- [ ] Audit log всех AI-действий в БД
- [ ] Cloud control plane (Next.js):
  - регистрация / NextAuth
  - привязка телефонов через QR-код
  - dashboard для удалённого управления
- [ ] Биллинг: Stripe + webhook + лимиты
- [ ] Тарифы:
  - Free: 100k токенов, локальные модели, read-only AI
  - Pro $9: 2M токенов, Claude Haiku, approval actions
  - Studio $29: 10M токенов, Claude Sonnet, расширенный marketplace
- [ ] Security review всех tools
- [ ] Документация для пользователей

**Критерий успеха:** через 3 месяца после запуска платного tier — 50+ платящих пользователей.

---

## Фаза 6 — Auto-mode и website creation (12 недель)

**Цель:** AI может не только править, но и создавать новые приложения с нуля. Аналог Bolt/Lovable, но с физическим хостингом.

### Чек-лист

- [ ] Spec-driven генерация:
  - пользователь описывает приложение текстом или голосом
  - AI генерирует начальный код (Next.js / Astro / Express шаблоны)
  - автоматический деплой
- [ ] Iterative editing:
  - "поменяй цвет hero блока на синий"
  - AI редактирует файлы, делает commit, передеплоивает
- [ ] Auto-mode для опытных пользователей:
  - whitelisted безопасные tools выполняются без подтверждения
  - destructive — всё равно через approval
- [ ] Версионирование AI-изменений: git history, rollback
- [ ] Уведомления о проактивных действиях AI: "обнаружил CVE в next@14.0.1, обновить?"

**Критерий успеха:** пользователь говорит "сделай лендинг для курса плавания" — через 5 минут есть рабочий сайт на собственном домене, размещённый на его телефоне.

---

## Параллельно весь срок

- Маркетинг: блог на vibefly.dev, посты на Reddit (r/selfhosted, r/homelab, r/androidroot), HN, Twitter
- Сообщество: Telegram-канал, Discord
- Документация: туториалы, troubleshooting, API reference
- Тесты: unit для критичного, e2e на физическом устройстве, CI на GitHub Actions
- Безопасность: review всех write-tools перед фазой 5, регулярные security audits

## Реалистичные сроки

При работе 2–3 часа в день параллельно с остальными проектами:

| Фаза | Идеально | Реально |
|---|---|---|
| 0 — PoC | 2 недели | 3–4 недели |
| 1 — Личная версия | 4 недели | 6–8 недель |
| 2 — Android APK v0.1 | 6 недель | 8–10 недель |
| 3 — Marketplace + Tunnel | 4 недели | 5–7 недель |
| 4 — AI Read-only | 6 недель | 8–10 недель |
| 5 — AI Write + биллинг | 10 недель | 14–18 недель |
| 6 — Auto-mode | 12 недель | 16–20 недель |
| **До коммерческой версии** | **9 месяцев** | **12–16 месяцев** |

При full-time фокусе — реальные сроки совпадают с идеальными.
