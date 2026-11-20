#!/usr/bin/env bash
# =============================================================================
# VM 2 - database machine. Run this ON the VM.
#   bash setup-db-vm.sh <this-VM-private-IP> <VM-1-private-IP>
# Prints the database password ONCE - copy it, you will type it on VM 1.
# =============================================================================
source "$(dirname "$0")/common.sh"
cd "$(dirname "$0")"

[ $# -eq 2 ] || die "Usage: bash setup-db-vm.sh <this-VM-private-IP> <VM-1-private-IP>"
DB_IP="$1"; APP_IP="$2"

say "1/4  Checking the machine"
require_ubuntu_arm_or_x86
ok "database will bind to $DB_IP, accept connections only from $APP_IP"

say "2/4  Docker"
install_docker

say "3/4  Password and container"
if [ ! -f .db.env ]; then
  PW=$(random_password)
  printf 'POSTGRES_DB=legalcase\nPOSTGRES_USER=legalcase\nPOSTGRES_PASSWORD=%s\n' "$PW" > .db.env
  chmod 600 .db.env
  NEW_PW=1
  ok "generated database password (stored in .db.env, mode 600)"
else
  PW=$(grep '^POSTGRES_PASSWORD=' .db.env | cut -d= -f2-)
  NEW_PW=0
  ok "keeping existing database password"
fi
DB_BIND_ADDRESS="$DB_IP" docker compose -f docker-compose.db.yml up -d
for i in $(seq 1 40); do
  docker exec legalcase-db pg_isready -U legalcase -d legalcase >/dev/null 2>&1 && break
  sleep 3
done
ok "PostgreSQL 16 running"

say "4/4  Network rules: only VM 1 may reach port 5432"
# Docker publishes ports through the FORWARD path, so filter there too.
# Docker rewrites the destination before FORWARD, so match the ORIGINAL destination port
# (conntrack) in DOCKER-USER. Also filter INPUT, which covers docker-proxy connections.
sudo iptables -N DOCKER-USER 2>/dev/null || true
if ! sudo iptables -C DOCKER-USER -p tcp -m conntrack --ctorigdstport 5432 ! -s "$APP_IP" -j DROP 2>/dev/null; then
  sudo iptables -I DOCKER-USER 1 -p tcp -m conntrack --ctorigdstport 5432 ! -s "$APP_IP" -j DROP
fi
if ! sudo iptables -C INPUT -p tcp --dport 5432 ! -s "$APP_IP" -j DROP 2>/dev/null; then
  sudo iptables -I INPUT 1 -p tcp --dport 5432 ! -s "$APP_IP" -j DROP
fi
persist_firewall
ok "port 5432 reachable from $APP_IP only"

echo
echo "============================================================"
echo "  Database ready on $DB_IP:5432 (database: legalcase, user: legalcase)"
if [ "$NEW_PW" = "1" ]; then
  echo "  Password (copy it now, shown once):  $PW"
else
  echo "  Password: stored in deploy/oracle/.db.env on this VM"
fi
echo "  Next: on VM 1 run  bash setup-app-vm.sh  and enter $DB_IP and the password."
echo "============================================================"
