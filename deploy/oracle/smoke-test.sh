#!/usr/bin/env bash
# LegalCase ERP - post-deploy smoke test.
# Usage:  bash smoke-test.sh http://<public-ip-or-domain>
# Exits 0 only if every check passes. Run it from any machine with curl and python3.
set -u
BASE="${1:-http://localhost:8080}"
BASE="${BASE%/}"
PASS=0; FAIL=0
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }
json() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)" 2>/dev/null; }

echo "Smoke test against $BASE"

# 1. health
[ "$(curl -s -m 10 "$BASE/api/health" | json "['status']")" = "ok" ] && ok "health endpoint" || bad "health endpoint"

# 2. login as attorney
TOKEN=$(curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
        -d '{"email":"ally@mongalalaw.co.za","password":"lawyer123"}' | json "['token']")
[ -n "$TOKEN" ] && ok "attorney login returns a token" || { bad "attorney login"; TOKEN=""; }
A="Authorization: Bearer $TOKEN"

# 3. data is present (seeded on first start)
N=$(curl -s -m 15 "$BASE/api/cases" -H "$A" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
[ "${N:-0}" -ge 1 ] && ok "cases present ($N)" || bad "cases present"

# 4. firm billing overview (AI-linked quantum)
[ "$(code -m 15 "$BASE/api/billing/overview" -H "$A")" = "200" ] && ok "billing overview" || bad "billing overview"

# 5. calendar status
[ "$(code -m 15 "$BASE/api/calendar/status" -H "$A")" = "200" ] && ok "calendar status" || bad "calendar status"

# 6. Excel workbook export is a real .xlsx (zip)
curl -s -m 30 "$BASE/api/reports/erp-workbook.xlsx" -H "$A" -o /tmp/lce-smoke.xlsx
if python3 -c "import zipfile,sys; zipfile.ZipFile('/tmp/lce-smoke.xlsx')" 2>/dev/null; then ok "xlsx workbook downloads"; else bad "xlsx workbook downloads"; fi
rm -f /tmp/lce-smoke.xlsx

# 7. audit chain intact
ATOK=$(curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
       -d '{"email":"admin@mongalalaw.co.za","password":"admin123"}' | json "['token']")
CHAIN=$(curl -s -m 20 "$BASE/api/admin/audit/verify" -H "Authorization: Bearer $ATOK")
echo "$CHAIN" | grep -q '"intact":true\|"valid":true\|"ok":true' && ok "audit chain verifies" || bad "audit chain verifies ($CHAIN)"

# 8. security: no token -> 401 ; client cannot read another client's case
[ "$(code -m 10 "$BASE/api/cases")" = "401" ] && ok "unauthenticated request refused (401)" || bad "unauthenticated request refused"
CT=$(curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
     -d '{"email":"thabo@example.com","password":"client123"}' | json "['token']")
[ "$(code -m 10 -X POST "$BASE/api/calendar/test-connection" -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}')" = "403" ] \
  && ok "client refused admin-only calendar action (403)" || bad "client refused admin-only calendar action"

# 9. lockout: 3 bad passwords -> 429 on the third (use a throw-away address)
for i in 1 2 3; do LAST=$(code -m 10 -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
     -d '{"email":"lockout-check@example.com","password":"wrong"}'); done
[ "$LAST" = "429" ] && ok "lockout returns 429 on third failure" || bad "lockout returns 429 (got $LAST)"

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
