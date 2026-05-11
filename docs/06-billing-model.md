# Billing model

## Тарифы (черновик)

### Free

- **$0/месяц**
- 100k AI токенов/месяц на локальных моделях (Llama 3.1 70B, Qwen Coder)
- Все PaaS-фичи без ограничений (деплои, логи, метрики, marketplace)
- Сколько угодно устройств в cloud-режиме
- Сообщественный support

### Pro

- **$9/месяц**
- 2M токенов/месяц
- Доступ к Claude Haiku, GPT-4o-mini, DeepSeek Coder
- Approval-based AI actions
- Email support 24h
- Приоритет на cloud туннелях

### Studio

- **$29/месяц**
- 10M токенов/месяц
- Claude Sonnet, GPT-5, Gemini Pro
- Auto-mode для whitelisted-операций (restart, health-check)
- Premium marketplace templates
- Email support 4h
- Custom domain support (vibefly.app/your-name)

### Enterprise / White-label

- **By contract** — обсуждается индивидуально
- Неограниченные токены (или BYOK)
- On-premise cloud control plane
- SSO, RBAC, audit log export
- Telegram-бот поддержки, SLA

## BYOK (Bring Your Own Key) tier

- **$5/месяц**
- Неограниченные токены (с твоими ключами к OpenAI / Anthropic / OpenRouter)
- Мы берём только за платформу, весь AI-биллинг идёт напрямую к провайдеру
- Для энтузиастов и privacy-paranoid

## Экономика

### Себестоимость (ориентиры 2026)

- Cloud control plane (VPS у автора, уже оплачен): $0 маржинально
- Cloudflare Workers (AI Router): по вызовам, при 100k MAU < $50/месяц
- Cloudflare R2 (rootfs CDN): $0.015/ГБ storage + free egress = ~$5–20/месяц
- AI tokens:
 - Llama 3.1 70B через Groq/Cerebras: ~$0.05–0.10 / 1M tokens → free tier быстро окупается
 - Claude Haiku: $0.80/$4.00 per 1M (in/out) → при 2M токенов примерно $5–6 себестоимость, Pro $9 даёт маржинально положительно
 - Claude Sonnet: $3.00/$15.00 per 1M → при 10M токенов себестоимость $25–40, Studio $29 — в балансе
 - DeepSeek Coder: $0.14/$0.28 per 1M → отличная вещь для free tier

### Ключевые принципы

1. **Free tier всегда живёт на дешёвых моделях.** Никогда не разоримся на free-users.
2. **Конкурентные цены.** Coolify Cloud — $5–20/месяц, Vercel — $20/месяц. Мы немного ниже по входу.
3. **Margin 30–50% на paid-tiers.** Компенсирует free tier и инфру.
4. **Чёткие лимиты.** Дошёл до лимита → UI показывает upgrade flow, не hidden charges.
5. **Pause vs cancel.** Pause замораживает серверы, но сохраняет данные. Cancel — полный delete через 30 дней.

## Stripe integration

- Webhook обработка в cloud control plane
- Reconciliation раз в сутки
- Failover при фейле оплаты: 3 retry в течение 7 дней, затем downgrade на Free тариф
- Биллинг токенов: храним в Postgres usage-events table, агрегируем по биллинг-периодам

## Не биллим

- Self-hosted версия без AI — всегда бесплатная (и без cloud control plane)
- Open-source ядро (AGPL-3.0)
- BYOK режим с пользовательским API key к OpenRouter — только platform-fee
