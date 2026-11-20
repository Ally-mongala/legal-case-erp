#!/bin/bash
set -e
B=http://localhost:8080
jq() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

echo "=== 1. LAWYER LOGIN (manual) ==="
TOKEN=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"ally@mongalalaw.co.za","password":"lawyer123"}' | jq "d['token']")
AUTH="Authorization: Bearer $TOKEN"
echo "token ok: ${TOKEN:0:12}..."

echo; echo "=== 2. FR1: UPLOAD WORD DOCUMENT (.docx) ==="
DOC=$(curl -s -X POST $B/api/cases/c2/documents -H "$AUTH" -F "file=@/tmp/vanwyk_claim.docx" -F "consent=true")
DOCID=$(echo "$DOC" | jq "d['id']")
echo "doc: $DOCID type=$(echo "$DOC" | jq "d['docType']") consent=$(echo "$DOC" | jq "d['consentGranted']") status=$(echo "$DOC" | jq "d['processingStatus']")"
for i in $(seq 1 20); do
  ST=$(curl -s $B/api/documents/$DOCID -H "$AUTH" | jq "d['processingStatus']")
  [ "$ST" = "PROCESSED" ] && break; [ "$ST" = "FAILED" ] && { echo FAILED; exit 1; }; sleep 1
done
echo "status: $ST"

echo; echo "=== 3. FR1: CONSENT REQUIRED CHECK (consent=false rejected) ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $B/api/cases/c1/documents -H "$AUTH" -F "file=@/tmp/mokoena_affidavit.pdf" -F "consent=false")
echo "consent=false -> HTTP $CODE (expect 400)"

echo; echo "=== 4. FR2: EXTRACTIONS (attorneys, judge, key facts) ==="
curl -s $B/api/documents/$DOCID/extractions -H "$AUTH" | python3 -c "
import sys,json
for e in json.load(sys.stdin):
    print(f\"  {e['fieldName']:<16} {e['fieldValue'][:52]:<54} conf={e['confidence']:.2f} p{e['pageNumber']}\")"

echo; echo "=== 5. FR3: SUMMARY (Facts / Legal Issues / Current Status) ==="
curl -s $B/api/documents/$DOCID/summaries -H "$AUTH" | python3 -c "
import sys,json
s=json.load(sys.stdin)
for i,p in enumerate([s['paragraph1'],s['paragraph2'],s['paragraph3']],1):
    print(f'  P{i}: {p[:150]}')"

echo; echo "=== 6. FR4: MANUAL DEADLINE ==="
curl -s -X POST $B/api/cases/c1/deadlines -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"File discovery affidavit","date":"2026-10-01","type":"FILING","reminderDays":3}' | jq "d['title'] + ' @ ' + d['date'] + ' reminder ' + str(d['reminderDays'])"

echo; echo "=== 7. FR7: CLIENT RATES LAWYER ==="
CT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"thabo@example.com","password":"client123"}' | jq "d['token']")
curl -s -X POST $B/api/ratings -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d '{"caseId":"c1","score":5,"comment":"Excellent service - highly recommended!"}' | jq "d['lawyerName'] + ' ' + str(d['score']) + '/5'"

echo; echo "=== 8. FR8: ADMIN LISTS USERS + REVOKES CLIENT ==="
AT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@mongalalaw.co.za","password":"admin123"}' | jq "d['token']")
curl -s $B/api/admin/users -H "Authorization: Bearer $AT" | python3 -c "
import sys,json
for u in json.load(sys.stdin): print(f\"  {u['name']:<16} {u['role']:<8} active={u['active']}\")"
curl -s -X POST $B/api/admin/users/u3/revoke -H "Authorization: Bearer $AT" | jq "d['message']"

echo; echo "=== 9. REVOKED USER CANNOT LOGIN ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"elna@example.com","password":"client123"}')
echo "elna login after revoke -> HTTP $CODE (expect 403)"

echo; echo "=== 10. FR9: DOWNLOAD AUDIT CSV ==="
curl -s $B/api/admin/reports/audit.csv -H "Authorization: Bearer $AT" -o /tmp/audit.csv
head -3 /tmp/audit.csv
echo "... ($(wc -l < /tmp/audit.csv) lines)"

echo; echo "=== 11. FR10: METRICS + TRAINING APPROVAL + DATASETS ==="
curl -s $B/api/admin/metrics -H "Authorization: Bearer $AT" | python3 -c "
import sys,json
m=json.load(sys.stdin)
for k in ['totalUsers','totalCases','processedDocuments','avgConfidence','totalRatings','trainingRuns','dataResidency','encryption']:
    print(f'  {k}: {m.get(k)}')"
curl -s -X POST $B/api/admin/training-runs/tr1/approve -H "Authorization: Bearer $AT" | jq "d['message']"
curl -s $B/api/admin/datasets -H "Authorization: Bearer $AT" | python3 -c "
import sys,json
for d in json.load(sys.stdin): print('  dataset:', d['name'], '-', d['records'], 'records')"

echo; echo "=== 12. ADMIN SCOPE CHECK: lawyer cannot hit admin endpoints ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" $B/api/admin/users -H "$AUTH")
echo "lawyer -> /api/admin/users : $CODE (expect 403)"
echo "ALL FR1-FR11 E2E TESTS DONE"
