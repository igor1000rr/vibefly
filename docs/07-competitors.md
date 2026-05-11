# Конкуренты и рынок

## Прямые конкуренты (Phone-as-Server)

**Главный инсайт:** прямых конкурентов нет ни одного. Ниша фактически свободна.

### Ближайшие аналоги

**Andronix** ($5 одноразово, ~500k installs)

- Что делает: установщик Linux поверх Termux
- Чего нет: управления приложениями, AI, marketplace, mobile UI

**UserLAnd** (open-source, ~200k installs)

- То же самое бесплатно
- Чего нет: всего вышеперечисленного

**kWS, AWebServer, Simple HTTP Server**

- Что делают: раздают статику с телефона
- Чего нет: не PaaS, только HTTP-файлы

**Droidspaces**

- Не конкурент. Namespace-runtime, низовый слой. Мы используем или пишем своё аналогичное.

## Непрямые конкуренты

### PaaS для VPS

- **Coolify Cloud** — $5–20/месяц. Self-hosted PaaS. Нет mobile UI, нет телефона.
- **Dokploy** — бесплатный self-hosted Coolify-клон.
- **CapRover** — open-source, требует VPS.
- **Dokku** — мини-версия Heroku.
- **Render, Railway, Fly.io** — managed PaaS, $7+/месяц.
- **Vercel, Netlify** — frontend-focused.

### AI-app builders

- **Vercel v0** — генерирует React-компоненты из промпта. Нет хостинга.
- **Bolt.new** — полные приложения в браузере. Хостится на StackBlitz.
- **Lovable** — SaaS без кода. Хостинг на их инфре.
- **Replit Agent** — AI-кодинг + деплой в Replit.
- **Cursor, Claude Code** — кодинг-ассистенты, нет деплоя.

### Self-hosted ROM

- **PostmarketOS, Ubuntu Touch** — заменяют Android. Не приложение. Не для массы.
- **LineageOS** — чистый Android, не про PaaS.

## Ценностные оси конкурентов

| | Генерация UI | Хостинг | AI после деплоя | На своём железе | Mobile-first |
|---|---|---|---|---|---|
| Vercel v0 | Да | Нет | Нет | Нет | Нет |
| Bolt.new | Да | StackBlitz | Нет | Нет | Нет |
| Lovable | Да | Их cloud | Нет | Нет | Нет |
| Replit | Да | Replit | Частично | Нет | Нет |
| Coolify | Нет | Своё | Нет | Да (VPS) | Нет |
| Andronix | Нет | Частично | Нет | Да (телефон) | Нет |
| **VibeFly** | Частично (фаза 5+) | Да | Да | Да (телефон) | Да |

## Наш уникальный угол

**AI работает в режиме живого сервера, а не одноразовой генерации.** Это отличает нас от всех AI-builders. Мы не пытаемся соревноваться с Bolt в качестве генерации — мы выигрываем в поддержке после деплоя.

**Телефон вместо сервера.** Coolify на VPS — $5+/месяц постоянно. VibeFly на телефоне — 0. Это $60 экономии в год на первый сервер, $300+ если у юзера пять пет-проектов.

**Мобильный UI.** Ни один из PaaS-конкурентов не сделал нормальный mobile-опыт. Мы стартуем именно с мобильного.

## Риски

- **Google Play policy** против server-apps. Решение: F-Droid первым, sideload-instructions, Aurora Store / RuStore.
- **Bolt/Lovable/Vercel выйдут на mobile хостинг.** Маловероятно в ближайшие 12–18 месяцев, у них другая стратегия.
- **Coolify mobile-app.** Гипотетически возможно, но Coolify изначально для VPS, не телефонов.
- **Android закручивает background-execution.** Каждый релиз OS жёстче к fg-services. Решение: root + Magisk-модули для power users, sideload для обычных.

## TAM (ориентир)

- r/selfhosted: 700k+ активных
- r/homelab: 1.5M
- r/androidroot: 200k
- Self-hosting в развивающихся странах: оценка в 5–10M разработчиков без VPS
- Eco / repair движение: миллионы в ЕС

Реалистичный addressable: 50k-500k installs за 12–18 месяцев при нормальном маркетинге. Conversion в paid: 2–5% → 1k–25k платящих в лучшем сценарии.
