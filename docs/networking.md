# Networking & VPS infrastructure

Как VibeFly выпускает телефон-сервер в интернет и какая серверная обвязка вокруг него живёт.

## Зачем вообще нужен tunnel

Мобильный телефон почти всегда сидит за CGNAT мобильного оператора или за домашним роутером. Публичный IP ему никто не выдаёт, порты прокинуть нельзя. Без reverse-tunnel из сети оператора VibeFly бесполезен — хостить можно только для себя на своём же Wi-Fi.

Поэтому ingress — обязательный компонент продукта, не опция. Он входит в первую линию PMR.

## Что мы выбрали: Cloudflare Tunnel

### Сравнение опций

| Опция | Цена | Домен | Auth | Плюсы | Минусы |
|---|---|---|---|---|---|
| **Cloudflare quick tunnel** | $0 | `*.trycloudflare.com` (случайный) | нет | mature, anycast, без регистрации | URL случайный при каждом старте |
| **Cloudflare named tunnel** | $0 | свой домен (CF DNS) | API токен | стабильный домен, WAF, Access | нужен домен в CF |
| **Tailscale Funnel** | $0 | `*.ts.net` | OAuth | mesh-режим, peer-to-peer | не anycast, медленнее в Азии |
| **ngrok** | $0–8/мес | свой/`*.ngrok-free.app` | API key | простой | free с ограничениями, платный для всерьёз |
| **Проброс через VPS в середине** | $5/мес | свой | SSH | полный контроль | своя инфра, вручную |

Cloudflare quick tunnel выигрывает по трём осям сразу: бесплатно, без регистрации (критично для onboarding), anycast из Беларуси/России работает быстрее Tailscale. Минус со случайным URL лечится named tunnel'ом когда появится свой домен.

### Реальная реализация

Go-пакет `internal/tunnel/` оборачивает бинарь `cloudflared`:

```
Manager interface {
    Start(ctx) (Status, error)
    Stop() error
    Status() Status
    Subscribe() <-chan Event
    Close() error
}
```

`Cloudflared` — реальная реализация поверх `exec.CommandContext`. Запускает:

```
cloudflared tunnel --url http://127.0.0.1:3001 --no-autoupdate
```

Читает stderr cloudflared, парсит regex'ом строку с публичным URL, возвращает её в Status.PublicURL. Stop посылает SIGTERM, ждёт 5 секунд, иначе SIGKILL.

`Nop` — заглушка для систем без cloudflared (macOS dev, CI). Все методы возвращают пустой статус/`ErrNotConfigured`, без паник.

## REST API

Три endpoints, все под бирер-токеном:

```
GET  /tunnel        → Status (active, public_url, started_at, provider, last_error)
POST /tunnel/start  → Status (блокирующий, ~5–30 сек)
POST /tunnel/stop   → Status
```

Полный schema — `apps/agent/docs/API.md`. Android-DTO — `TunnelStatusDto` в `apps/android/.../agent/Dto.kt`.

## Конфигурация агента

```toml
[tunnel]
enabled = false        # включить manager (иначе endpoints вернут 503)
autostart = false      # поднять туннель сразу при boot
binary = "cloudflared" # пусто = искать в $PATH
target = ""            # пусто = http://<listen>
startup_timeout = "60s"
```

## Где cloudflared взять

В rootfs он устанавливается из официального Cloudflare apt-репо на stage 4 в `runtime/rootfs-builder/build.sh`. GPG-подпись проверяется, обновления пойдут вместе с `apt-get upgrade`.

Для локальных тестов на macOS/Linux дев-хосте: `brew install cloudflared` или `wget` .deb с https://github.com/cloudflare/cloudflared/releases.

## Что дальше

**Named tunnels (фаза 3+).** Когда будет свой домен (например `vibefly.app`), пользователь будет получать стабильный `my-name.vibefly.app` вместо случайного `*.trycloudflare.com`. Для этого нужны API токен Cloudflare + `cloudflared tunnel route dns`. Provider в этом режиме будет `"named"`.

**Cloud control plane.** Для named tunnels нужен лёгкий backend на нашей стороне — он раздаёт пользователям их поддомены и привязывает DNS-записи к tunnel ID'ам в Cloudflare API. План — поднять на Coolify (`vibecoding-coolify`), эндпоинт вроде `api.vibefly.app/devices/<id>/tunnel`.

**Tailscale параллельный backend.** Некоторые пользователи хотят P2P в свою локальную сеть без выхода в публик. Tailscale Manager реализует тот же interface `tunnel.Manager`, так что UI и endpoints не меняются.

**Apps-level ingress.** Сейчас туннель один на весь агент (проксирует :3001). Дальше сделаем на nginx внутри rootfs reverse-прокси по Host хедеру: `app1.your-name.vibefly.app` в :3002, `app2.your-name.vibefly.app` в :3003 и т.д. В named-режиме cloudflared в этом случае проксирует wildcard *.<name>.vibefly.app.
