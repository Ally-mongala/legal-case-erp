#!/usr/bin/env bash
# Shared helpers for the Oracle setup scripts.
set -euo pipefail

say()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()   { printf '    \033[1;32mOK\033[0m  %s\n' "$*"; }
warn() { printf '    \033[1;33mNOTE\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31mSTOP:\033[0m %s\n' "$*" >&2; exit 1; }

require_ubuntu_arm_or_x86() {
  [ -r /etc/os-release ] || die "This script is for Ubuntu on Oracle Cloud."
  . /etc/os-release
  case "$ID" in ubuntu|debian) ;; *) die "Unsupported OS ($ID). Use the Ubuntu 22.04 or 24.04 image.";; esac
  ok "OS: $PRETTY_NAME ($(uname -m))"
}

install_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    ok "Docker and Compose already installed"
    return
  fi
  say "Installing Docker (official script, works on ARM and x86)"
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER" || true
  ok "Docker installed"
}

# Docker's own published ports bypass the INPUT chain, so we also filter FORWARD via DOCKER-USER.
# Opening a port on the host firewall is idempotent (checks before inserting).
open_host_port() {
  local port="$1"
  if ! sudo iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
    sudo iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
  fi
  ok "host firewall allows TCP $port"
}

persist_firewall() {
  if ! dpkg -s iptables-persistent >/dev/null 2>&1; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq iptables-persistent netfilter-persistent >/dev/null
  fi
  sudo netfilter-persistent save >/dev/null
  ok "firewall rules saved (survive reboot)"
}

private_ip() {
  # the VM's address on the VCN subnet (10.x.x.x or 172.16.x.x)
  ip -4 -o addr show scope global | awk '{print $4}' | cut -d/ -f1 | grep -E '^(10\.|172\.1[6-9]\.|172\.2[0-9]\.|172\.3[01]\.|192\.168\.)' | head -1
}

random_secret() { openssl rand -hex 32; }
random_password() { openssl rand -base64 30 | tr -dc 'A-Za-z0-9' | head -c 32; }
