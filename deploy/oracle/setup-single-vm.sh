#!/usr/bin/env bash
# =============================================================================
# ONE VM setup (Oracle Always Free). Run ON the VM from the repo:
#   cd ~/legal-case-erp/deploy/oracle && bash setup-single-vm.sh
# Generates all secrets itself. Safe to re-run: existing secrets are kept.
# =============================================================================
source "$(dirname "$0")/common.sh"
cd "$(dirname "$0")"

say "1/5  Checking the machine"
require_ubuntu_arm_or_x86

say "2/5  Docker"
install_docker

say "3/5  Secrets and settings (generated, nothing to type)"
[ -f .db.env ] || { printf 'POSTGRES_DB=legalcase\nPOSTGRES_USER=legalcase\nPOSTGRES_PASSWORD=%s\n' "$(random_password)" > .db.env; chmod 600 .db.env; }
DBPW=$(grep '^POSTGRES_PASSWORD=' .db.env | cut -d= -f2-)
[ -f .env ] || { cp env.example .env; chmod 600 .env; }
set_env() {
  python3 - "$1" "$2" <<'PYEOF'
import sys, os
key, val = sys.argv[1], sys.argv[2]
lines = open(".env").read().splitlines() if os.path.exists(".env") else []
out, done = [], False
for line in lines:
    if line.startswith(key + "="): out.append(f"{key}={val}"); done = True
    else: out.append(line)
if not done: out.append(f"{key}={val}")
open(".env", "w").write("\n".join(out) + "\n")
PYEOF
  chmod 600 .env
}
get_env() { grep "^$1=" .env | head -1 | cut -d= -f2-; }
[ -n "$(get_env LCE_JWT_SECRET)" ]        || set_env LCE_JWT_SECRET "$(random_secret)"
[ -n "$(get_env LCE_ENCRYPTION_KEY)" ]    || set_env LCE_ENCRYPTION_KEY "$(random_secret)"
set_env SITE_ADDRESS ":80"
# DB_PASSWORD is read by docker compose from the shell; keep it in .env as well so re-runs match
set_env DB_PASSWORD "$DBPW"
ok "JWT key, document-encryption key and database password ready (kept in .env / .db.env, mode 600)"

say "4/5  Firewall (ports 80 and 443 only)"
open_host_port 80
open_host_port 443
persist_firewall
warn "Also open ports 80 and 443 in the Oracle console (VCN > Security List > Ingress Rules)."

say "5/5  Building and starting (first build: 5-10 minutes)"
docker compose -f docker-compose.single.yml --env-file .env up -d --build
for i in $(seq 1 60); do
  if docker exec legalcase-web wget -qO- http://backend:8080/api/health 2>/dev/null | grep -q '"ok"'; then
    ok "backend healthy"; break
  fi
  sleep 5
  [ "$i" -eq 60 ] && die "backend not healthy. Run: docker compose -f docker-compose.single.yml logs backend"
done

PUBLIC_IP=$(curl -fs -m 5 https://ifconfig.me || echo "<public-ip>")
echo
echo "============================================================"
echo "  LegalCase ERP is running."
echo "  Open:  http://$PUBLIC_IP"
echo "  Check: bash smoke-test.sh http://localhost:80   (expect 10 passed)"
echo "============================================================"
