# Roadmap VibeFly

План по фазам. Каждая фаза заканчивается чем-то работающим — на следующую переходим только после прохождения критерия успеха.

## Фаза 0 — Proof of Concept (2–3 недели)

**Цель:** доказать, что Redmi Note 14S (или похожий бюджетник) реально может быть продакшн-сервером.

- [ ] Проверить совместимость ядра с Droidspaces (Settings → Requirements в APK)
- [ ] Если требования не выполнены — выбрать путь: кастомное ядро / fallback на proot
- [ ] Поставить Termux + Termux:Boot + Termux:API из F-Droid
- [ ] Внутри: proot-distro install debian, базовые пакеты
- [ ] Cloudflare Tunnel настроен, поддомен живой
- [ ] Перенесён один реальный сервис (тестовый telegram-бот)
- [ ] 24-часовой стресс-тест без падений
- [ ] Логирование battery, temp, RAM каждые 5 минут
- [ ] Решение Magisk vs KernelSU-Next

**Критерий:** реальный сервис прожил 7 дней, температура < 50°C, Tunnel стабилен.

## Фаза 1 — Личная dogfooding-версия (4–6 недель)

**Цель:** VibeFly работает у автора как личная утилита.

- [ ] Telephone-agent v0.1 на Go (REST API минимум)
- [ ] PWA UI v0.1 на Next.js (главный дашборд)
- [ ] WebSocket для лайв-логов
- [ ] Деплой из git URL (clone + build + pm2 start)
- [ ] Env-management через UI
- [ ] 2–3 реальных сервиса автора живут на телефоне

**Критерий:** автор не открывает ssh для повседневного управления.

## Фаза 2 — Embedded APK (8–10 недель)

**Цель:** превратить связку Termux+UI в один установочный APK.

- [ ] Android-проект на Kotlin + Compose
- [ ] Foreground Service для удержания агента
- [ ] Запуск Droidspaces (или proot) из APK
- [ ] rootfs.img собран через debootstrap (Debian 12 ARM64 minimal)
- [ ] Bundling rootfs.img в APK (через split APK или scoped storage)
- [ ] BootReceiver для автостарта
- [ ] Первый альфа-APK на тестовом устройстве

**Критерий:** установка APK → открытие → работающий сервер за < 2 минуты.

## Фаза 3 — Open-source запуск (4 недели)

**Цель:** v0.1.0 для энтузиастов в r/selfhosted, r/homelab.

- [ ] Landing на vibefly.dev
- [ ] Видео-демо
- [ ] Документация: quick start, troubleshooting, FAQ
- [ ] Multi-tenant cloud-аккаунты (минимально)
- [ ] Onboarding wizard в APK
- [ ] Marketplace v0.1 (10 one-click приложений)
- [ ] Reddit / HackerNews / Habr посты
- [ ] Перевод репозитория в public

**Критерий:** 200+ звёзд GitHub, 20+ внешних установок, 5+ активных пользователей.

## Фаза 4 — AI Read-only (6 недель)

**Цель:** AI-ассистент видит сервер и объясняет.

- [ ] AI Router на Cloudflare Worker (multi-provider)
- [ ] Tools read-only: get_logs, get_metrics, list_apps, explain_error
- [ ] Чат-UI в APK
- [ ] Биллинг токенов в Postgres
- [ ] Free tier на локальных моделях (Llama / Qwen)
- [ ] Pro tier с Claude Haiku / GPT-4o-mini

**Критерий:** AI отвечает на вопросы про сервер с 80% полезностью.

## Фаза 5 — AI с действиями + биллинг (12 недель)

**Цель:** AI выполняет операции с подтверждением, есть платящие.

- [ ] Tools write-mode с approval flow: suggest_restart, suggest_env_change, suggest_db_migration, suggest_deploy
- [ ] Audit log всех AI-действий
- [ ] Stripe интеграция, тарифы Free / Pro $9 / Studio $29
- [ ] Failover между AI-провайдерами
- [ ] Auto-mode (только для Pro/Studio, whitelisted tools)
- [ ] Security review всех tools

**Критерий:** 50+ платящих пользователей через 3 месяца, MRR покрывает инфру.

## Параллельно (с фазы 3)

- Документация: quick start, self-hosting guide, API reference, architecture overview
- Community: Discord или Telegram-канал
- Blog: 1 пост раз в 2–4 недели
- Тесты: unit для критичных функций, e2e на физическом устройстве
- CI: GitHub Actions для всех компонентов
