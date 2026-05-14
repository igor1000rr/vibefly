# Phase 2: Linux runtime via chroot

## Цель

Запускать на телефоне приложения которые требуют настоящего Linux-окружения:
Node.js, Python, Ruby, PostgreSQL, Redis. Без Termux и без proot — настоящий
chroot внутрь Alpine Linux 3.20.

## Архитектура

### Rootfs

- **Base:** Alpine 3.20 minirootfs aarch64 (~3 MB), bundled в APK
- **Read-only слой:** распаковывается в `filesDir/rootfs-base/` при первом
  старте агента (`rootfs.Manager.EnsureBase`)
- **Per-app overlay:** для каждого приложения — свой writable слой через
  overlayfs (`upper` + `work`) поверх shared base.
  Если overlayfs недоступен в ядре — fallback на per-app копию (медленнее
  при install, но работает везде).

### Сеть

- Bind-mount `/etc/resolv.conf` хоста в chroot — иначе `apk add` и
  `npm install` не смогут резолвить хосты.
- `/proc`, `/sys`, `/dev/null`, `/dev/urandom`, `/dev/pts` — bind-mount
  из хоста.
- Port-forwarding: приложение внутри chroot слушает `0.0.0.0:8080`,
  хост видит на `127.0.0.1:8080` — chroot не меняет network namespace.

### Process management

- `ChrootSupervisor` — новый backend рядом с `ExecSupervisor`.
- `AppSpec.Runtime` поле: `""` (native, default) / `"chroot"` / `"chroot-debian"` (TBD).
- При `Runtime == "chroot"` роутер перенаправляет install/start/stop в
  ChrootSupervisor вместо ExecSupervisor.

## Roadmap

| Step | Статус | Описание |
|------|---------|----------|
| 2.1  | Делаем   | Bundle Alpine в APK + `rootfs.Manager.EnsureBase` |
| 2.2  | TODO    | `ChrootSupervisor` + AppSpec.Runtime поле |
| 2.3  | TODO    | overlayfs / per-app copy fallback |
| 2.4  | TODO    | `apk add nodejs` в hook после install (раз на приложение) |
| 2.5  | TODO    | UI: Runtime radio button в Deploy form |
| 2.6  | TODO    | Marketplace templates: Node.js Hello World, Python Flask |

## Ограничения этапа 2

- Требует root на телефоне (для mount /proc /sys /dev и chroot).
  Без root — chroot-runtime отключен, приложения запускаются
  как раньше — нативные arm64 бинари.
- glibc-бинари не работают в Alpine (musl). Для Node.js это ОК —
  `apk add nodejs` ставит musl-версию.
- Размер APK растёт на ~3 MB.
