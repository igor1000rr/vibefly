# Droidspaces integration notes

Droidspaces — LXC-inspired namespace-runtime для Android. Мы используем его как fundament для запуска Debian-rootfs с systemd внутри VibeFly APK.

## Ссылки

- GitHub: https://github.com/ravindu644/Droidspaces-OSS
- Telegram: https://t.me/Droidspaces
- License: GPL v3

## Почему Droidspaces

- Единственный zero-dependency namespace-runtime с systemd PID 1
- Single static binary 150 КБ
- Активная разработка в 2026
- Поддержка cgroups v1 + v2, overlay, pivot_root, adaptive seccomp

## Что нужно проверить на устройстве

Перед установкой VibeFly APK проверяет требования Droidspaces через встроенный Requirements-checker:

- `CONFIG_USER_NS`, `CONFIG_PID_NS`, `CONFIG_NET_NS`, `CONFIG_IPC_NS`, `CONFIG_UTS_NS`, `CONFIG_CGROUPS`
- `pivot_root` сискол
- OverlayFS
- KernelSU-Next (рекомендуется) или Magisk (экспериментально)

## Лицензионные вопросы

GPL v3 заразной, но совместим с нашим AGPL-3.0. При коммерческой версии (Pro/Studio cloud control plane) рассмотрим варианты:

1. Оставить GPL-версию как embedded runtime, выделив процесс-барьер (общение через IPC, не прямые вызовы)
2. Договориться с ravindu644 о dual license
3. Написать собственный namespace-launcher (~2000 строк C, 2-3 месяца)

## Fallback strategy

Если Droidspaces не запускается на конкретном устройстве — fallback на proot через termux-bundle, embedded в APK. Изоляция слабее (нет PID namespace), но работает почти на любом Android.

## TODO

- [ ] Протестировать на Redmi Note 14S (HyperOS + Magisk)
- [ ] Проверить совместимость ядра (Requirements check)
- [ ] Решить GPL strategy до фазы 5
