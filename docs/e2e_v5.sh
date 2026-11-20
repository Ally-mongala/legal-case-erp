#!/bin/bash
B=http://localhost:8080
j() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
LT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"ally@mongalalaw.co.za","password":"lawyer123"}' | j "d['token']")
LA="Authorization: Bearer $LT"

echo "=== 1. DASHBOARD DATA (landing view in connected mode) ==="
echo "  cases: $(curl -s $B/api/cases -H "$LA" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")"
echo "  deadlines: $(curl -s $B/api/deadlines -H "$LA" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")"
echo "  avg rating: $(curl -s $B/api/ratings/average -H "$LA" | j "str(d['average']) + ' / ' + str(d['count'])")"
echo "  unread: $(curl -s $B/api/me/unread-lawyer -H "$LA" | j "d['unread'])")"

echo; echo "=== 2. AI VALIDATION - BLOCK WRONG DOC (sample_legal = Peter Dube v Quickfix on case c1) ==="
D=$(curl -s -X POST $B/api/cases/c1/documents -H "$LA" -F "file=@/tmp/sample_legal.pdf" -F "consent=true" | j "d['id']")
for i in $(seq 1 20); do S=$(curl -s $B/api/documents/$D -H "$LA" | j "d['processingStatus']"); [ "$S" != "PROCESSING" ] && break; sleep 1; done
echo "  status: $S (expect REJECTED)"
curl -s $B/api/documents/$D -H "$LA" | j "d['rejectionReason']" | head -c 160; echo
echo "  extractions kept (highlighted): $(curl -s $B/api/documents/$D/extractions -H "$LA" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))") fields"

echo; echo "=== 3. AI VALIDATION - ACCEPT MATCHING DOC (mokoena_affidavit on case c1) ==="
D2=$(curl -s -X POST $B/api/cases/c1/documents -H "$LA" -F "file=@/tmp/mokoena_affidavit.pdf" -F "consent=true" | j "d['id']")
for i in $(seq 1 20); do S2=$(curl -s $B/api/documents/$D2 -H "$LA" | j "d['processingStatus']"); [ "$S2" != "PROCESSING" ] && break; sleep 1; done
echo "  status: $S2 (expect PROCESSED)"
echo "  summary: $(curl -s $B/api/documents/$D2/summaries -H "$LA" | j "d['paragraph1'][:60]")"
echo "  full text chars: $(curl -s $B/api/documents/$D2/content -H "$LA" | j "d['characters']")"

echo; echo "=== 4. PER-CASE CALENDAR .ICS (each case) ==="
for c in c1 c2 c3; do
  EV=$(curl -s $B/api/cases/$c/calendar.ics -H "$LA" | grep -c BEGIN:VEVENT)
  echo "  case $c: $EV synced event(s) in its .ics"
done
echo "ALL V5 (DASHBOARD/BLOCK/IMMEDIATE/PER-CASE-ICS) TESTS DONE"
