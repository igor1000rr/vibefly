# Rootfs builder

Сборка Debian 12 (bookworm) ARM64 rootfs.img, который впоследствии упаковывается в VibeFly APK или раздаётся через CDN.

## Что внутри образа

- Debian 12 bookworm minbase
- systemd + dbus
- Node.js 22 из NodeSource + pm2 + pnpm
- python3, build-essential, git, nginx-light
- VibeFly-агент в `/usr/local/bin/vibefly-agent` + systemd-unit

Полный список пакетов — в [packages.txt](packages.txt).

## Сборка локально (Linux + root)

```bash
sudo apt install -y debootstrap qemu-user-static binfmt-support zstd e2fsprogs

# собрать агента под ARM64 заранее
cd ../../apps/agent
make arm64

cd ../../runtime/rootfs-builder
sudo AGENT_BIN="$PWD/../../apps/agent/bin/vibefly-agent-arm64" ./build.sh
```

Результат в `out/rootfs.img.zst`. Ожидаемый размер — около 200–300 МБ сжатый, ~1.2 ГБ развёрнутый.

## Сборка в CI

GitHub Actions рабочий процесс в [.github/workflows/rootfs.yml](../../.github/workflows/rootfs.yml):

1. Собирает агента под ARM64
2. Запускает build.sh на ubuntu-latest с qemu-user-static
3. Публикует артефакт `rootfs-arm64`
4. При релизе заливает в GitHub Releases + опционально в Cloudflare R2

## Развёртывание на телефоне

VibeFly APK при первом запуске скачивает этот образ в `/data/data/by.vibefly.app/files/rootfs/`, разворачивает и запускает через namespace-runtime (Droidspaces или fallback).

## Роудмап образа

- v0.1 (сейчас): базовый образ с Node 22, Python 3, nginx, агентом
- v0.2: + cloudflared, tailscale, certbot
- v0.3: + opcionally postgresql 16, redis 7
- v0.4: сплит на base + addon-образы (postgres-addon.img и т.п.)
