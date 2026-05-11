# Security model

## Уровни безопасности

### Android sandbox

Всё работает внутри sandbox-папки Android-приложения `/data/data/by.vibefly.app/`. Другие приложения не имеют доступа. Удаление APK вычищает всё.

### namespace-runtime (Droidspaces)

Каждое user-app внутри rootfs живёт в своём systemd unit с ограничениями:

```ini
[Service]
MemoryMax=512M
CPUQuota=80%
PrivateTmp=yes
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
```

### Секреты

- Общие токены (AI provider keys, Cloudflare API): `EncryptedSharedPreferences` на Android-стороне
- App-specific env: в SQLite-базе агента, encrypted-at-rest через SQLCipher
- Никогда не логируются в plain text
- Маскирование при выводе в UI (`****1234`)

### Local-only auth

UI общается с агентом только через `127.0.0.1:3001`. Token генерируется при первом запуске, сохраняется в EncryptedSharedPreferences и в rootfs-файле с правами `0600`. Другие приложения на телефоне его не видят.

### Cloud sync (опциональный)

Если пользователь включает cloud-режим:

- WebSocket-соединение агента с `cloud.vibefly.dev` → TLS + mTLS сертификаты
- Device-token ротируется раз в 30 дней
- Cloud никогда не видит пользовательские secrets — они живут только на устройстве

## AI-tool security

### Классы действий

**Read-only (no approval):**

- `get_logs(app, timeframe)`
- `get_metrics(timeframe)`
- `list_apps()`
- `get_app_config(app)`
- `explain_error(log_excerpt)`

**Mutating (require approval):**

- `suggest_restart(app)`
- `suggest_env_change(app, key, new_value)` — UI показывает diff
- `suggest_db_migration(sql)` — UI показывает SQL preview и EXPLAIN
- `suggest_deploy(app, branch)`
- `suggest_install_app(template, env)`

**Destructive (require explicit approval и cooldown):**

- `delete_app(app)` — требует повторного подтверждения имени приложения
- `drop_table(table)` — то же самое
- `reset_device()` — никогда не вызываем из AI в v1

### Tool validation

Каждый tool-call проходит валидацию:

1. **Schema validation** — входные параметры строго типизированы, никакого свободного shell
2. **Whitelist** — `run_cmd` принимает только команды из фиксированного списка (`git pull`, `pm2 reload`, etc.)
3. **Sandboxing** — выполнение внутри user-app context, не от root
4. **Rate limiting** — не более N tools в минуту
5. **Audit log** — все tool calls пишутся в audit.db с timestamp, user input, AI response, result

### Prompt injection защита

- AI промпты отделены от user content через strict XML delimiters
- Tool outputs обрезаются до фиксированных размеров, подозрительные паттерны (фразы вроде "ignore previous instructions") флагируются
- Пользовательский вход никогда не интерпретируется как system prompt
- Для mutating tools всегда требуется human-in-the-loop

## Сетевая безопасность

- Default: ничего не слушается на 0.0.0.0
- Всё локальное живёт на `127.0.0.1`
- Внешний доступ только через Cloudflare Tunnel или Tailscale
- Все user-apps по умолчанию не выходят наружу, пользователь явно включает expose-on-domain

## Threat model

### Что защищаем

- Данные пользовательских приложений (базы, env, файлы)
- Секреты (токены, API keys)
- Конфиденциальность переписки с AI

### От кого

- Других приложений на том же телефоне (Android sandbox)
- Сетевых атак (всё localhost по умолчанию)
- AI prompt injection (валидация tools)
- Кражи телефона (encrypted-at-rest + Android lock screen)

### От чего НЕ защищаем

- Целенаправленных nation-state атак
- Compromised root / вредоносных Magisk-модулей
- Физического доступа с forensics-тулами без блокировки экрана

## Disclosure

Багрепорты по безопасности — личным сообщением в Telegram [@igor1000rr](https://t.me/igor1000rr). Bug bounty после выхода v1.0.
