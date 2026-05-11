# Rootfs builder

Скрипты сборки Debian 12 ARM64 образа, который поставляется внутри APK.

## Статус

Не начат. Планируется к фазе 2.

## Общий процесс

1. `debootstrap` собирает минимальный Debian rootfs (variant=minbase)
2. chroot → установка пакетов из `packages.txt`
3. Копирование агента + systemd unit
4. Настройка дефолтных конфигов nginx, sshd, systemd-journald
5. Сжатие в ext4 image через `mkfs.ext4 -d`
6. zstd-сжатие для финального размера

## Где собирается

GitHub Actions на ARM64 runner (Linux self-hosted или GitHub-managed ARM64). Билд раз в неделю + при каждом релизе.

Aptifact публикуется как GitHub Release asset + копия в Cloudflare R2.
