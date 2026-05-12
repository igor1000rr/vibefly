#!/usr/bin/env bash
# build.sh — сборка Debian 12 ARM64 rootfs для VibeFly.
#
# Результат: out/rootfs.img + out/rootfs.img.zst.
# Запускать под root на Linux хосте или в GitHub Actions на ubuntu-latest.
#
# Зависимости:
#   apt: debootstrap qemu-user-static binfmt-support zstd e2fsprogs
#
# Переменные окружения:
#   ARCH       — arm64 (default)
#   SUITE      — bookworm (default)
#   MIRROR     — http://deb.debian.org/debian (default)
#   IMG_SIZE   — 1500M (default; подбирать под реальный размер)
#   AGENT_BIN  — путь к vibefly-agent-arm64 (если есть, копируется в /usr/local/bin)

set -euo pipefail

ARCH="${ARCH:-arm64}"
SUITE="${SUITE:-bookworm}"
MIRROR="${MIRROR:-http://deb.debian.org/debian}"
IMG_SIZE="${IMG_SIZE:-1500M}"
AGENT_BIN="${AGENT_BIN:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${ROOT_DIR}/out"
ROOTFS_DIR="${OUT_DIR}/rootfs"
IMG_FILE="${OUT_DIR}/rootfs.img"
IMG_ZST="${OUT_DIR}/rootfs.img.zst"

log() { printf '\033[36m[rootfs]\033[0m %s\n' "$*"; }
err() { printf '\033[31m[rootfs]\033[0m %s\n' "$*" >&2; }

require_root() {
  if [[ $EUID -ne 0 ]]; then
    err "скрипт нужно запускать под root"
    exit 1
  fi
}

require_tools() {
  local missing=()
  for tool in debootstrap qemu-aarch64-static mkfs.ext4 zstd; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      missing+=("$tool")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    err "не хватает инструментов: ${missing[*]}"
    err "apt install -y debootstrap qemu-user-static binfmt-support zstd e2fsprogs"
    exit 1
  fi
}

cleanup() {
  log "размапим псевдо-фс"
  for fs in proc sys dev/pts dev; do
    if mountpoint -q "${ROOTFS_DIR}/${fs}"; then
      umount -lf "${ROOTFS_DIR}/${fs}" || true
    fi
  done
}
trap cleanup EXIT

stage_debootstrap() {
  log "stage 1: debootstrap ${SUITE} ${ARCH}"
  mkdir -p "${ROOTFS_DIR}"
  if [[ ! -f "${ROOTFS_DIR}/etc/os-release" ]]; then
    debootstrap \
      --arch="${ARCH}" \
      --variant=minbase \
      --foreign \
      --include=ca-certificates,curl,wget,gnupg \
      "${SUITE}" "${ROOTFS_DIR}" "${MIRROR}"

    cp /usr/bin/qemu-aarch64-static "${ROOTFS_DIR}/usr/bin/"
    chroot "${ROOTFS_DIR}" /debootstrap/debootstrap --second-stage
  else
    log "rootfs уже существует, пропускаем debootstrap"
  fi
}

bind_mounts() {
  log "монтируем псевдо-фс в chroot"
  mount -t proc /proc "${ROOTFS_DIR}/proc"
  mount --bind /sys "${ROOTFS_DIR}/sys"
  mount --bind /dev "${ROOTFS_DIR}/dev"
  mount --bind /dev/pts "${ROOTFS_DIR}/dev/pts"
}

stage_packages() {
  log "stage 2: установка пакетов"
  bind_mounts

  cp "${ROOT_DIR}/packages.txt" "${ROOTFS_DIR}/tmp/packages.txt"
  cat >"${ROOTFS_DIR}/etc/apt/sources.list" <<EOF
deb ${MIRROR} ${SUITE} main contrib non-free-firmware
deb ${MIRROR} ${SUITE}-updates main contrib non-free-firmware
deb http://security.debian.org/debian-security ${SUITE}-security main contrib non-free-firmware
EOF

  chroot "${ROOTFS_DIR}" bash -euxc '
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    grep -vE "^\s*(#|$)" /tmp/packages.txt | xargs -r apt-get install -y --no-install-recommends
    apt-get clean
    rm -rf /var/lib/apt/lists/*
  '
}

stage_node() {
  log "stage 3: Node.js 22 из NodeSource"
  chroot "${ROOTFS_DIR}" bash -euxc '
    export DEBIAN_FRONTEND=noninteractive
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
    apt-get install -y --no-install-recommends nodejs
    npm install -g pm2 pnpm
    apt-get clean
    rm -rf /var/lib/apt/lists/*
  '
}

stage_cloudflared() {
  # cloudflared — обязательный бинарь для публичного tunnel'а. Агент запускает
  # его по POST /tunnel/start, поэтому без него ingress не работает. Ставим
  # из официального Cloudflare apt-репо (свежие версии, GPG-подпись их).
  log "stage 4: cloudflared из официального Cloudflare apt-репо"

  chroot "${ROOTFS_DIR}" bash -euxc "
    export DEBIAN_FRONTEND=noninteractive
    mkdir -p --mode=0755 /usr/share/keyrings
    curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg \
      | tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null
    echo 'deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared ${SUITE} main' \
      > /etc/apt/sources.list.d/cloudflared.list
    apt-get update
    apt-get install -y --no-install-recommends cloudflared
    apt-get clean
    rm -rf /var/lib/apt/lists/*
    cloudflared --version
  "
}

stage_agent() {
  log "stage 5: агент + systemd unit"

  install -d -m 0755 "${ROOTFS_DIR}/etc/vibefly"
  install -d -m 0755 "${ROOTFS_DIR}/var/lib/vibefly/apps"
  install -d -m 0755 "${ROOTFS_DIR}/var/log/vibefly"
  install -d -m 0755 "${ROOTFS_DIR}/usr/local/bin"

  if [[ -n "${AGENT_BIN}" && -f "${AGENT_BIN}" ]]; then
    install -m 0755 "${AGENT_BIN}" "${ROOTFS_DIR}/usr/local/bin/vibefly-agent"
  else
    log "AGENT_BIN не задан — агент будет доложен позже CI-пайплайном"
  fi

  install -m 0644 "${ROOT_DIR}/../../apps/agent/config/agent.example.toml" \
    "${ROOTFS_DIR}/etc/vibefly/agent.toml"
  install -m 0644 "${ROOT_DIR}/../../apps/agent/config/vibefly-agent.service" \
    "${ROOTFS_DIR}/etc/systemd/system/vibefly-agent.service"

  chroot "${ROOTFS_DIR}" systemctl enable vibefly-agent.service
}

stage_finalize() {
  log "stage 6: финальный cleanup"
  chroot "${ROOTFS_DIR}" bash -euxc '
    rm -rf /var/cache/apt/* /var/lib/apt/lists/* /var/log/*.log /tmp/*
    apt-get autoremove -y --purge
  '
  rm -f "${ROOTFS_DIR}/usr/bin/qemu-aarch64-static"
}

stage_pack() {
  log "stage 7: упаковка в ext4 + zstd"
  cleanup  # размапим перед mkfs

  rm -f "${IMG_FILE}"
  truncate -s "${IMG_SIZE}" "${IMG_FILE}"
  mkfs.ext4 -F -d "${ROOTFS_DIR}" -L vibefly-rootfs "${IMG_FILE}"

  log "сжимаем zstd -19"
  zstd -19 -f --rm "${IMG_FILE}" -o "${IMG_ZST}"

  log "готово: ${IMG_ZST}"
  ls -lh "${IMG_ZST}"
}

main() {
  require_root
  require_tools
  mkdir -p "${OUT_DIR}"

  stage_debootstrap
  stage_packages
  stage_node
  stage_cloudflared
  stage_agent
  stage_finalize
  stage_pack
}

main "$@"
