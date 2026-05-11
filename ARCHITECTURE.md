# Архитектура VibeFly

Документ для контрибьюторов и для нас самих. Здесь — зачем выбран каждый слой и как они соединяются.

## Принципы

1. **Один APK для пользователя.** Никакого Termux, F-Droid, ssh, отдельных дистрибутивов. Установка в один тап.
2. **Настоящий Linux внутри.** Не proot-эмуляция, а namespaces с systemd как PID 1. Coolify и подобные должны теоретически запускаться без модификаций.
3. **AI как middleware, не как магия.** Все его действия видны пользователю, любое разрушительное — через подтверждение. Read-only по умолчанию, write — только с явного "apply".
4. **Локально первое, облако опциональное.** Без VPS, без cloud-аккаунта, без интернета — приложение работает в локальной сети. Облако — только для удалённого управления и AI без BYOK.

## Уровни системы

### Уровень 0 — Android-хост

Стоковый Android (HyperOS, MIUI, One UI, любой). Root приветствуется, но базовый функционал работает без него.

С root:
- Полное снятие Doze / App Standby для агента
- Открытие портов < 1024 напрямую (80/443 без проксирования)
- Магическое управление зарядкой (30–80% лимит через ACC / AccA)
- Настоящие cgroups и namespaces для контейнеров

Без root:
- Foreground Service + WakeLock держит агента живым
- Только порты ≥ 1024, всё остальное через Cloudflare Tunnel
- Базовая работа через Termux-free namespace-wrapper

### Уровень 1 — Container runtime

**Droidspaces** (https://github.com/ravindu644/Droidspaces-OSS) — наш предпочтительный движок.

Что даёт:
- PID, mount, UTS, IPC, cgroup namespaces
- systemd как PID 1 внутри контейнера
- OverlayFS для эфемерных режимов
- Cgroup-учёт ресурсов
- Hardware passthrough (GPU, sensors)
- 150KB статический бинарник на musl

Альтернативы (fallback если Droidspaces не работает на устройстве пользователя):
- proot (как у Termux) — медленнее, без systemd
- chroot с ручной настройкой namespaces — для энтузиастов

### Уровень 2 — Rootfs

**Debian 12 ARM64 (bookworm)**, минимальный server-профиль.

Что внутри:
- systemd, journald, networkd
- node 20 LTS, python 3.11, go 1.21
- pm2 как менеджер пользовательских приложений
- nginx как reverse proxy
- postgresql 15 (опциональный сервис, не запускается по умолчанию)
- redis 7 (опциональный)
- cloudflared
- наш агент `vibefly-agent` как systemd-юнит

Размер: ~600–800 МБ в сжатом виде, ~1.5 ГБ после распаковки.

Собирается через `runtime/rootfs-builder/build.sh` в GitHub Actions, артефакт релизится как `rootfs-debian-12-arm64.img.xz`.

### Уровень 3 — Agent

Go-сервер, лежит в `/usr/local/bin/vibefly-agent` внутри Debian. Запускается как `systemctl enable vibefly-agent.service` при первом старте контейнера.

Что делает:
- **REST API** на `localhost:7070` — управление приложениями
- **WebSocket** для лайв-логов и метрик
- **PM2 wrapper** — внутри запускает pm2 для пользовательских процессов
- **Git operations** — clone, pull, deploy hooks
- **Metrics collector** — читает `/sys/class/power_supply`, `/proc/*`, `pm2 jlist`
- **AI tools endpoint** — структурированный набор операций, который AI может вызывать

Авторизация — Bearer-токен, генерируется при первом запуске и записывается в keystore Android-приложения.

### Уровень 4 — Android UI

Kotlin + Jetpack Compose. **Не WebView.** Нативный UI, потому что UX это половина продукта.

Структура:
- `MainActivity` — точка входа
- `VibeFlyForegroundService` — держит runtime живым
- `ContainerManager` — взаимодействие с Droidspaces через JNI
- `AgentClient` — HTTP/WS клиент к localhost:7070
- `UI/` — экраны на Compose, по нашим мокапам

Экраны (из мокапов):
- Dashboard — статус устройства + список приложений
- AppDetail — overview, logs, env, deploys, domain
- AssistantChat — AI с tool calls и approval flow
- Marketplace — каталог one-click приложений
- DeployNew — форма / promtp-first создание
- Settings — connections, AI provider, device limits, backups

### Уровень 5 — Cloud (опционально)

Next.js 15 на одном из VPS. Это не обязательная часть — приложение работает без облака.

Что даёт облако:
- Удалённый доступ к телефону-серверу с любого устройства
- Multi-tenancy (один пользователь — несколько телефонов)
- AI Router без BYOK (платная подписка с включёнными токенами)
- Marketplace-каталог приложений
- Биллинг, лимиты, аналитика

Без облака:
- AI работает только в BYOK-режиме (свой ключ OpenAI / Anthropic)
- Управление только локально по Wi-Fi или Tailscale
- Marketplace встроен в APK как статический каталог

## Поток данных

### Запуск приложения

```
Пользователь тапает Start
  ↓
ForegroundService стартует
  ↓
ContainerManager (через JNI) запускает Droidspaces:
  droidspaces start --rootfs /data/data/dev.vibefly/rootfs.img --name main
  ↓
Внутри контейнера: systemd → vibefly-agent.service
  ↓
Agent слушает localhost:7070
  ↓
UI коннектится к Agent через HTTP/WS
  ↓
Готово. Главный экран показывает Battery/Temp/Apps.
```

### Деплой приложения

```
Пользователь в UI: New App
  ↓ git URL + домен
UI → POST /apps {git, domain, branch}
  ↓
Agent:
  1. git clone в /var/vibefly/apps/<id>
  2. определяет тип (package.json, requirements.txt, go.mod)
  3. устанавливает зависимости
  4. собирает (если нужно)
  5. pm2 start с генерацией ecosystem.config.js
  6. nginx config для домена, reload
  7. cloudflared tunnel route (если домен через CF)
  ↓
WebSocket стримит прогресс в UI
  ↓
UI обновляет статус → Running
```

### AI tool call

```
Пользователь в чате: "почему лагает azcrm"
  ↓
UI → Cloud AI Router (или прямо к BYOK-провайдеру)
  ↓
LLM выбирает tools: get_logs, get_metrics, db_slow_queries
  ↓
Tool calls идут обратно к Agent через WebSocket
  ↓
Agent выполняет, возвращает результат
  ↓
LLM анализирует, предлагает решение
  ↓
Если решение разрушительное → UI показывает Approve/Cancel
Если безопасное → выполняется автоматически
  ↓
Результат + audit log
```

## Где живёт что

| Файл / процесс | Где |
|---|---|
| APK | /data/app/dev.vibefly.app/ |
| Android-данные приложения | /data/data/dev.vibefly.app/ |
| Rootfs.img | /data/data/dev.vibefly.app/files/rootfs.img |
| Droidspaces бинарник | /data/data/dev.vibefly.app/files/droidspaces |
| Agent (внутри контейнера) | /usr/local/bin/vibefly-agent |
| User apps (внутри контейнера) | /var/vibefly/apps/<id>/ |
| Logs | /var/log/vibefly/ (внутри контейнера) |
| pm2 home | /var/vibefly/.pm2/ |
| Конфиги пользователя | /data/data/dev.vibefly.app/shared_prefs/ |

## Что НЕ делается в APK

- Никакой генерации UI приложений на лету (этим занимается AI снаружи или через cloud-проводник).
- Никакого embedded LLM. Языковые модели — через сеть (Cloudflare Worker → провайдер).
- Никакого собственного криптографического стека — используем стандартный Java/Android KeyStore.
- Никаких proprietary бинарников. Всё, что прилетает в APK, либо open-source, либо собрано нами.

## Безопасность

- **Изоляция контейнера от Android.** Контейнер не видит другие Android-приложения, не имеет доступа к контактам, фото, и т.д.
- **AI sandbox.** Tools жёстко типизированы и валидируются. Никакого `exec(arbitrary_string)`.
- **Approval-mode по умолчанию.** Любое действие, которое меняет состояние (deploy, env, restart), требует подтверждения пользователя в UI.
- **Audit log.** Все действия AI пишутся в `/var/log/vibefly/ai-audit.log`. Никакого скрытого исполнения.
- **Secrets маскируются.** При показе env в UI значения с ключевыми словами (password, token, secret, key) маскируются. AI видит только маски, не значения.
- **Outbound сеть мониторится.** Опциональный режим — UI показывает куда стучатся приложения. Если что-то странное — сразу видно.

## Что меняется при отсутствии Droidspaces

Если устройство не поддерживает namespaces (старое ядро, отсутствие CONFIG_USER_NS, и т.д.):

- Используется **proot fallback** (как у Termux).
- Внутри нет настоящего systemd — заменяется на supervisord или runit.
- Меньше изоляции, но всё остальное работает.
- В UI пишется "Limited mode" с объяснением.

Это путь B на случай, если на Redmi Note 14S Droidspaces не заведётся.
