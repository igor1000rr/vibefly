# Roadmap VibeFly

План по фазам. Каждая фаза заканчивается чем-то работающим — на следующую переходим только после прохождения критерия успеха.

## Фаза 0 — Proof of Concept

**Цель:** доказать, что Redmi Note 14S (или похожий бюджетник) реально может быть продакшн-сервером. Агент и UI работают локально.

- [x] Telephone-agent v0.1 на Go (REST API + WebSocket логов)
- [x] UI v0.1 на Kotlin + Compose, 5 экранов в скевоморфизме iOS 6
- [x] Builder Debian 12 ARM64 rootfs в CI (`.img.zst` ~280 MB)
- [x] Demo-mode в APK — работает без агента и без рута
- [x] Cloudflare Tunnel в агенте (quick tunnel + REST API + Mock)
- [x] cloudflared устанавливается в rootfs из официального Cloudflare apt-репо

## Фаза 1 — Личный dogfooding

**Цель:** VibeFly работает у автора как личная утилита. 2–3 сервиса живут на телефоне и доступны из интернета.

- [ ] gradle wrapper сгенерирован и закоммичен — CI начнёт компилить Kotlin
- [ ] JNI bridge к Droidspaces (ждём Requirements скрин с устройства)
- [ ] Тест на 2–3 устройствах парка (Pixel + S10 + Redmi)
- [ ] Tunnel UI в Settings — toggle Start/Stop, копирование публичного URL
- [ ] Foreground Service для удержания агента при свёрнутом аппе

## Фаза 2 — Embedded APK

**Цель:** один установочный APK — работающий сервер за < 2 минуты.

- [ ] rootfs.img внутри APK (Android Asset Pack или scoped storage)
- [ ] BootReceiver для автостарта
- [ ] Тест на всём парке (Pixel / S10 / OneTouch / Redmi)
- [ ] Onboarding wizard: rootfs unpack → agent start → tunnel up

## Фаза 3 — Public launch

**Цель:** публичный релиз для энтузиастов.

- [ ] Sign APK + F-Droid metadata
- [ ] README на английском, перевод репозитория в public
- [ ] Cloudflare named tunnels: свой домен `vibefly.app`, поддомены вида `my-name.vibefly.app`
- [ ] Cloud control plane на Coolify — раздаёт поддомены, привязывает DNS
- [ ] Reddit / HackerNews / Habr посты

## Фаза 4 — AI read-only

**Цель:** AI-ассистент видит сервер и объясняет.

- [ ] AI Router на Cloudflare Worker (multi-provider)
- [ ] CloudflareProxyAiClient заменяет StubAiClient
- [ ] Tools read-only: list_apps, get_logs, system_metrics (уже в ToolRegistry)
- [ ] Free tier на локальных моделях (Llama 3.1 70B через Groq, DeepSeek Coder)

## Фаза 5 — AI с действиями

**Цель:** AI выполняет операции с подтверждением.

- [ ] Tools write-mode с approval flow: restart_app, stop_app (уже в ToolRegistry)
- [ ] Расширение ToolRegistry: db_migrate, deploy, env_change
- [ ] Audit log всех AI-действий
- [ ] Security review tool definitions

## Потом — монетизация

Биллинг и тарифы — если будет аудитория и спрос. Не приоритет. Гипотеза описана в `docs/06-billing-model.md`.

## Параллельно

- Документация: quick start, self-hosting guide, API reference, architecture overview
- Tests: unit для критичных функций, e2e на физическом устройстве
- CI: GitHub Actions для всех компонентов
