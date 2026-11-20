#!/usr/bin/env bash
# =============================================================================
#  demo-preflight.sh  -  run this 10 minutes before you present
#
#  It checks that everything needed for the live demonstration is ready, and
#  prints PASS or FAIL for each item. Nothing is changed - it only looks.
#
#  Usage:   bash demo-preflight.sh              (default http://localhost:8080)
#           bash demo-preflight.sh http://localhost:8081
# =============================================================================
set -u
B="${1:-http://localhost:8080}"
PASS=0; FAIL=0

ok()   { printf "  \033[32mPASS\033[0m  %s\n" "$1"; PASS=$((PASS+1)); }
bad()  { printf "  \033[31mFAIL\033[0m  %s\n" "$1"; FAIL=$((FAIL+1)); }
warn() { printf "  \033[33mNOTE\033[0m  %s\n" "$1"; }
head_() { printf "\n%s\n" "$1"; }

printf "\n==============================================================\n"
printf "  LegalCase ERP - pre-flight check\n"
printf "  Backend: %s\n" "$B"
printf "  Time:    %s\n" "$(date '+%Y-%m-%d %H:%M:%S')"
printf "==============================================================\n"

# ---------------------------------------------------------------- 1. Java
head_ "1. Java version (must be 21)"
if command -v java >/dev/null 2>&1; then
  JV="$(java -version 2>&1 | head -1)"
  if echo "$JV" | grep -q '"21'; then ok "Java 21 found: $JV"
  else bad "Java is NOT 21 -> $JV  (select JDK 21 and open a new terminal)"; fi
else
  bad "java not found on PATH"
fi

# ---------------------------------------------------------------- 2. Backend
head_ "2. Backend is running"
if curl -s -m 5 "$B/api/health" | grep -q '"status":"ok"'; then
  ok "Health endpoint answered {\"status\":\"ok\"}"
else
  bad "No answer from $B/api/health  (is the backend started?)"
fi

if curl -s -m 5 -o /dev/null -w "%{http_code}" "$B/" | grep -q 200; then
  ok "Browser client served at $B/"
else
  bad "The web page did not load at $B/"
fi

# ---------------------------------------------------------------- 3. Logins
head_ "3. The three demo accounts can sign in"
login() {  # $1 email  $2 password  $3 label
  local code
  code=$(curl -s -m 8 -o /tmp/pf_login.json -w "%{http_code}" -X POST "$B/api/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$1\",\"password\":\"$2\"}")
  if [ "$code" = "200" ] && grep -q '"token"' /tmp/pf_login.json 2>/dev/null; then
    ok "$3 sign-in ($1)"
  else
    bad "$3 sign-in failed ($1) -> HTTP $code"
  fi
}
login "ally@mongalalaw.co.za"  "lawyer123" "Attorney"
login "thabo@example.com"      "client123" "Client"
login "admin@mongalalaw.co.za" "admin123"  "Administrator"

# ---------------------------------------------------------------- 4. Data
head_ "4. Seeded data is present"
TOK=$(curl -s -m 8 -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
      -d '{"email":"ally@mongalalaw.co.za","password":"lawyer123"}' \
      | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
AUTH="Authorization: Bearer $TOK"

if [ -n "$TOK" ]; then
  CNT=$(curl -s -m 8 -H "$AUTH" "$B/api/cases" | grep -o '"id"' | wc -l | tr -d ' ')
  if [ "${CNT:-0}" -ge 4 ]; then ok "Case list returned $CNT matters"
  else bad "Case list returned only ${CNT:-0} matters (expected 4 or more)"; fi

  if curl -s -m 8 -H "$AUTH" "$B/api/cases/c1" | grep -q 'Mokoena'; then
    ok "Demo case C-2026-014 (Mokoena v Siyaya) is present"
  else
    warn "Could not confirm C-2026-014 by id c1 - check the case list on screen"
  fi

  if curl -s -m 8 -H "$AUTH" "$B/api/deadlines" | grep -q '"id"'; then
    ok "Deadlines are present (calendar demo will work)"
  else
    warn "No deadlines returned - the calendar slide will look empty"
  fi

  if curl -s -m 10 -H "$AUTH" "$B/api/cases/c1/calendar.ics" | grep -q 'BEGIN:VCALENDAR'; then
    ok ".ics export works (fallback for Google Calendar)"
  else
    warn ".ics export did not return a calendar file"
  fi
else
  bad "Could not get a token, so the data checks were skipped"
fi

# ---------------------------------------------------------------- 5. Files
head_ "5. Upload files are ready"
HERE="$(cd "$(dirname "$0")" && pwd)"
DIR="$HERE/docs/demo-upload"
if [ -d "$DIR" ]; then
  N=0
  while IFS= read -r f; do
    case "$f" in *.pdf|*.docx|*.txt) N=$((N+1));; esac
  done < <(ls "$DIR")
  if [ "$N" -ge 8 ]; then ok "$N upload files found in docs/demo-upload/"
  else bad "Only $N upload files found in docs/demo-upload/ (expected 8)"; fi
  for must in "01 MATCH" "03 BLOCK" "06 FAILED" "07 INVALID"; do
    if ls "$DIR" | grep -q "^$must"; then ok "Present: $must ..."
    else bad "Missing file starting with \"$must\""; fi
  done
else
  bad "Folder docs/demo-upload/ not found"
fi

# ---------------------------------------------------------------- 6. Optional
head_ "6. Optional checks"
if command -v mvn >/dev/null 2>&1; then ok "Maven found (needed only to rebuild)"
else warn "Maven not on PATH - fine if you already have the built JAR"; fi

if [ -f "$HERE/backend/target/legal-backend.jar" ]; then
  ok "Pre-built JAR exists (your fallback)"
else
  warn "No built JAR found - build one before you travel: mvn clean install -DskipTests"
fi

# ---------------------------------------------------------------- summary
printf "\n==============================================================\n"
printf "  RESULT:  %d passed, %d failed\n" "$PASS" "$FAIL"
if [ "$FAIL" -eq 0 ]; then
  printf "  \033[32mReady to present.\033[0m\n"
else
  printf "  \033[31mFix the FAIL items above before you start.\033[0m\n"
fi
printf "==============================================================\n\n"
exit 0
