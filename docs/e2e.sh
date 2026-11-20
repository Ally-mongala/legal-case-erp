#!/bin/bash
set -e
B=http://localhost:8080
echo "=== 1. LAWYER LOGIN ==="
TOKEN=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"ally@mongalalaw.co.za","password":"lawyer123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
echo "token: ${TOKEN:0:25}..."
AUTH="Authorization: Bearer $TOKEN"

echo; echo "=== 2. GET /api/cases (list) ==="
curl -s $B/api/cases -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d),'cases:', [c['reference'] for c in d])"

echo; echo "=== 3. GET /api/cases?q=siyaya (search) ==="
curl -s "$B/api/cases?q=siyaya" -H "$AUTH" | python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d),'match(es)')"

echo; echo "=== 4. UPLOAD PDF to case c1 (POST /api/cases/c1/documents) ==="
DOC=$(curl -s -X POST $B/api/cases/c1/documents -H "$AUTH" -F "file=@/tmp/mokoena_affidavit.pdf")
echo "$DOC" | python3 -c "import sys,json;d=json.load(sys.stdin);print('doc id:',d['id'],'status:',d['processingStatus'])"
DOCID=$(echo "$DOC" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

echo; echo "=== 5. POLL status (worker pipeline) ==="
for i in $(seq 1 20); do
  ST=$(curl -s $B/api/documents/$DOCID -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['processingStatus'])")
  echo "  poll $i: $ST"
  [ "$ST" = "PROCESSED" ] && break
  [ "$ST" = "FAILED" ] && { echo "FAILED!"; exit 1; }
  sleep 1
done

echo; echo "=== 6. EXTRACTIONS (confidence + spans + page) ==="
curl -s $B/api/documents/$DOCID/extractions -H "$AUTH" | python3 -c "
import sys,json
for e in json.load(sys.stdin):
    print(f\"  {e['fieldName']:<18} {e['fieldValue'][:40]:<42} conf={e['confidence']:.2f} page={e['pageNumber']}\")"

echo; echo "=== 7. SUMMARY (3 paragraphs) ==="
curl -s $B/api/documents/$DOCID/summaries -H "$AUTH" | python3 -c "
import sys,json
s=json.load(sys.stdin)
print('status:',s['status'],'version:',s['version'])
for i,p in enumerate([s['paragraph1'],s['paragraph2'],s['paragraph3']],1):
    print(f'  P{i}: {p[:120]}...')"
SUMID=$(curl -s $B/api/documents/$DOCID/summaries -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

echo; echo "=== 8. DEADLINES created from extracted dates ==="
curl -s $B/api/cases/c1/deadlines -H "$AUTH" | python3 -c "
import sys,json
for d in json.load(sys.stdin): print(' ', d['title'], d['date'], d['type'], 'synced=',d['synced'])"

echo; echo "=== 9. APPROVE summary ==="
curl -s -X POST $B/api/summaries/$SUMID/approve -H "$AUTH" | python3 -c "import sys,json;print('approved, status:',json.load(sys.stdin)['status'])"

echo; echo "=== 10. CALENDAR SYNC ALL ==="
curl -s -X POST $B/api/calendar/sync-all -H "$AUTH"

echo; echo "=== 11. ACTIVITY (audit trail) ==="
curl -s "$B/api/activity?limit=6" -H "$AUTH" | python3 -c "
import sys,json
for a in json.load(sys.stdin): print(f\"  [{a['level']:<7}] {a['action']:<20} {a['details'][:70]}\")"

echo; echo "=== 12. CLIENT LOGIN + client-view ==="
CT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"thabo@example.com","password":"client123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s $B/api/cases/c1/client-view -H "Authorization: Bearer $CT" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print('  case:', d['legalCase']['reference'])
print('  approved summary:', d['summary'].get('status','NONE') if d['summary'] else 'NONE')
print('  deadlines:', len(d['deadlines']), 'docs:', len(d['documents']), 'messages:', len(d['messages']), 'unread:', d['unread'])"

echo; echo "=== 13. CLIENT SENDS MESSAGE ==="
curl -s -X POST $B/api/cases/c1/messages -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{"body":"Please call me about the settlement offer."}' | python3 -c "import sys,json;print('sent:',json.load(sys.stdin)['id'])"

echo; echo "=== 14. UNAUTHORIZED ACCESS CHECK (client hits lawyer endpoint) ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" $B/api/activity -H "Authorization: Bearer $CT")
echo "  client -> /api/activity : $CODE (expect 403)"
CODE2=$(curl -s -o /dev/null -w "%{http_code}" $B/api/cases -H "Authorization: Bearer $CT")
echo "  client -> /api/cases : $CODE2 (expect 403)"
CODE3=$(curl -s -o /dev/null -w "%{http_code}" $B/api/cases)
echo "  no token -> /api/cases : $CODE3 (expect 401)"
echo "ALL E2E TESTS DONE"
