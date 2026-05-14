# Deploy from GitHub repo

VibeFly умеет деплоить приложение по ссылке на GitHub-репо без ручного ввода всех
параметров — хватит `vibefly.toml` в корне репо.

## Как это работает

Агент НЕ клонирует репо и НЕ собирает код (на телефоне нет git/go/node — это фаза 2).
Вместо этого он читает манифест и скачивает уже готовый бинарь (обычно из GitHub Releases).

## Формат vibefly.toml

Полный пример:

```toml
name = "My App"
binary_url = "https://github.com/me/myapp/releases/latest/download/myapp-arm64.tar.gz"
start_cmd = "./myapp serve"
port = 8080
memory_max = "256M"
cpu_quota = "50%"
autostart = true
restart_policy = "on-failure"

[env]
DATABASE_URL = "sqlite:///data.db"
LOG_LEVEL = "info"
```

Поля:

- `name` — человеческое имя (UI). Если пусто — используется имя репо.
- `binary_url` — прямая HTTPS ссылка на бинарь или архив.
  Поддерживается:
  - одиночный бинарь → положит в `workdir/binary`, chmod +x
  - `.tar.gz` / `.tgz` / `.zip` → распакует в `workdir`
- `start_cmd` — команда запуска в `workdir`. По умолчанию `./binary` если бинарь без архива.
- `port` — порт на `127.0.0.1` на котором слушает приложение. Нужен для Publish (Cloudflare Tunnel).
- `memory_max`, `cpu_quota` — cgroup v2 лимиты. Требуют root на телефоне.
- `autostart` — стартовать при лоаде агента (после ребута телефона).
- `restart_policy` — `""` (никогда), `on-failure`, `always`.
- `[env]` — переменные окружения.

## REST API

### Deploy by repo

```
POST /apps/from-repo
Authorization: Bearer <token>
Content-Type: application/json

{
  "repo_url": "https://github.com/owner/repo",
  "branch": "main",
  "id": "my-app"
}
```

- `repo_url` — обязателен
- `branch` — опционально (default: `main` → fallback на `master`)
- `id` — опционально (default: имя репо)

### Preview manifest

Для UI чтобы показать юзеру что будет задеплоено перед подтверждением:

```
GET /manifest/preview?repo_url=https://github.com/owner/repo&branch=main
```

## Пример: Go HTTP-сервер

1. В своём репо добавь GitHub Actions который билдит бинарь под `linux/arm64`:

   ```yaml
   - name: Build arm64
     run: |
       GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o myapp-arm64 .
       tar czf myapp-arm64.tar.gz myapp-arm64
   - uses: softprops/action-gh-release@v2
     with:
       files: myapp-arm64.tar.gz
   ```

2. Добавь `vibefly.toml` в корень:

   ```toml
   name = "My HTTP server"
   binary_url = "https://github.com/me/myapp/releases/latest/download/myapp-arm64.tar.gz"
   start_cmd = "./myapp-arm64"
   port = 8080
   autostart = true
   restart_policy = "on-failure"
   ```

3. В VibeFly подай `POST /apps/from-repo` с `repo_url`. Агент скачает и запустит.

## Ограничения

- Нет компиляции на телефоне — бинарь должен быть уже собран под `linux/arm64`.
- Node.js / Python пока не работают (нужен интерпретатор в окружении — фаза 2: chroot + Debian).
- Приватные репо не поддерживаются (нет token forwarding). Релизы и raw.githubusercontent.com — public-only.
- Максимальный размер бинаря/архива — 200 MB.
