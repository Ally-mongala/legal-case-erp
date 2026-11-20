#!/usr/bin/env bash
# =============================================================================
# VM 1 - application machine. Run this ON the VM (after ssh), from the repo.
#   cd ~/legal-case-erp/deploy/oracle && bash setup-app-vm.sh
# Asks only what it needs. Safe to run again: existing secrets are kept.
# =============================================================================
source "$(dirname "$0")/common.sh"
cd "$(dirname "$0")"

say "1/6  Checking the machine"
require_ubuntu_arm_or_x86
ok "private IP: $(private_ip || echo unknown)"

say "2/6  Docker"
install_docker

say "3/6  Configuration"
if [ ! -f .env ]; then
  cp env.example .env
  chmod 600 .env
  ok "created .env from env.example"
fi

# set_env KEY VALUE - writes the line safely (no sed, so passwords with | & / are fine)
set_env() {
  python3 - "$1" "$2" <<'PYEOF'
import sys, os
key, val = sys.argv[1], sys.argv[2]
lines = open(".env").read().splitlines() if os.path.exists(".env") else []
out, done = [], False
for line in lines:
    if line.startswith(key + "="):
        out.append(f"{key}={val}"); done = True
    else:
        out.append(line)
if not done:
    out.append(f"{key}={val}")
open(".env", "w").write("\n".join(out) + "\n")
PYEOF
  chmod 600 .env
}
get_env() { grep "^$1=" .env | head -1 | cut -d= -f2-; }

if [ -z "$(get_env LCE_JWT_SECRET)" ]; then set_env LCE_JWT_SECRET "$(random_secret)"; ok "generated JWT secret"; else ok "keeping existing JWT secret"; fi
if [ -z "$(get_env LCE_ENCRYPTION_KEY)" ]; then set_env LCE_ENCRYPTION_KEY "$(random_secret)"; ok "generated document-encryption key"; else ok "keeping existing encryption key"; fi

if [ -z "${SITE_ADDRESS:-}" ]; then
  read -r -p "Domain name for HTTPS (press Enter to use plain HTTP by IP): " SITE_ADDRESS || true
fi
if [ -n "${SITE_ADDRESS:-}" ]; then set_env SITE_ADDRESS "$SITE_ADDRESS"; ok "public address: $SITE_ADDRESS"; else set_env SITE_ADDRESS ":80"; ok "public address: plain HTTP on port 80"; fi

if [ -z "${DB_HOST:-}" ]; then read -r -p "Private IP of VM 2 (the database machine), e.g. 10.0.0.x: " DB_HOST; fi
[ -n "$DB_HOST" ] || die "A database address is required."
if [ -z "${DB_PASSWORD:-}" ]; then read -r -s -p "Database password (printed by setup-db-vm.sh on VM 2): " DB_PASSWORD; echo; fi
[ -n "$DB_PASSWORD" ] || die "A database password is required."
set_env LCE_DB_URL "jdbc:postgresql://$DB_HOST:5432/legalcase?sslmode=disable"
set_env LCE_DB_USER "legalcase"
set_env LCE_DB_PASSWORD "$DB_PASSWORD"
ok "database: $DB_HOST:5432 (private network)"

say "4/6  Host firewall"
open_host_port 80
open_host_port 443
persist_firewall
warn "Also open ports 80 and 443 in the Oracle console: Networking > VCN > Security List > Ingress Rules."

say "5/6  Building and starting the application (first build takes 5-10 minutes)"
docker compose -f docker-compose.app.yml up -d --build
ok "containers started"

say "6/6  Waiting for the backend to become healthy"
for i in $(seq 1 60); do
  if docker exec legalcase-web wget -qO- http://backend:8080/api/health >/dev/null 2>&1 || \
     curl -fs -m 5 http://localhost:80/api/health >/dev/null 2>&1; then
    ok "backend healthy"
    break
  fi
  sleep 5
  [ "$i" -eq 60 ] && die "backend did not become healthy. Run: docker compose -f docker-compose.app.yml logs backend"
done

PUBLIC_IP=$(curl -fs -m 5 https://ifconfig.me || echo "<your-public-ip>")
echo
echo "============================================================"
echo "  LegalCase ERP is running."
echo "  Open:  ${SITE_ADDRESS:-http://$PUBLIC_IP}   (or https://$PUBLIC_IP if you set a domain)"
echo "  Test:  bash smoke-test.sh http://localhost:80"
echo "============================================================"
