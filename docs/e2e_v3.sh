#!/bin/bash
set -e
B=http://localhost:8080
j() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
LT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"ally@mongalalaw.co.za","password":"lawyer123"}' | j "d['token']")
LA="Authorization: Bearer $LT"
CT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"thabo@example.com","password":"client123"}' | j "d['token']")
CA="Authorization: Bearer $CT"
AT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@mongalalaw.co.za","password":"admin123"}' | j "d['token']")
AA="Authorization: Bearer $AT"

echo "=== 1. CALENDAR SYNC/UNSYNC CASE (lawyer) ==="
echo "  before:"; curl -s $B/api/cases/c1/deadlines -H "$LA" | python3 -c "import sys,json;d=json.load(sys.stdin);print('   synced:',sum(1 for x in d if x['synced']),'/',len(d))"
curl -s -X POST $B/api/cases/c1/calendar-unsync -H "$LA" | j "d['message']"
curl -s -X POST $B/api/cases/c1/calendar-sync -H "$LA" | j "d['message']"

echo; echo "=== 2. EDIT CASE STATUS (lawyer) + client alerted ==="
curl -s -X PATCH $B/api/cases/c1/status -H "$LA" -H 'Content-Type: application/json' -d '{"status":"PENDING_HEARING"}' | j "d['reference'] + ' -> ' + d['status']"
curl -s $B/api/cases/c1/messages -H "$CA" | python3 -c "import sys,json;d=json.load(sys.stdin);print('  last client msg:', d[-1]['subject'], '|', d[-1]['body'][:60])"

echo; echo "=== 3. UPDATE CASE (lawyer) + client alerted ==="
curl -s -X PATCH $B/api/cases/c1 -H "$LA" -H 'Content-Type: application/json' -d '{"description":"Quantum hearing scheduled. Settlement discussions ongoing.","amountInvolved":1300000}' | j "d['reference'] + ' amount=' + str(d['amountInvolved'])"
curl -s $B/api/cases/c1/messages -H "$CA" | python3 -c "import sys,json;d=json.load(sys.stdin);print('  last client msg:', d[-1]['subject'])"

echo; echo "=== 4. LAWYER MESSAGE + UNREAD ALERT ==="
curl -s -X POST $B/api/cases/c1/messages/lawyer -H "$LA" -H 'Content-Type: application/json' -d '{"subject":"Quantum documents","body":"Please send your latest payslips."}' | j "d['subject']"
curl -s $B/api/me/unread -H "$CA" | j "'  client unread: ' + str(d['unread'])"
curl -s -X POST $B/api/cases/c1/messages -H "$CA" -H 'Content-Type: application/json' -d '{"body":"I will email them today."}' | j "d['id']"
curl -s $B/api/me/unread-lawyer -H "$LA" | j "'  lawyer unread: ' + str(d['unread'])"
curl -s -X POST $B/api/cases/c1/messages/read-by-lawyer -H "$LA" | j "'  after mark-read: ' + str(d['ok'])"
curl -s $B/api/me/unread-lawyer -H "$LA" | j "'  lawyer unread now: ' + str(d['unread'])"

echo; echo "=== 5. LAWYER DOWNLOADS ACTIVITY/AUDIT CSV ==="
curl -s $B/api/activity.csv -H "$LA" -o /tmp/act.csv && echo "  audit.csv lines: $(wc -l < /tmp/act.csv) | head: $(head -1 /tmp/act.csv)"

echo; echo "=== 6. CLIENT DOWNLOADS SHARED DOC (real decrypted bytes) ==="
DOCID=$(curl -s $B/api/cases/c1/documents -H "$CA" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d[0]['id'] if d else '')")
curl -s $B/api/documents/$DOCID/download -H "$CA" -o /tmp/dl.bin
echo "  downloaded bytes: $(wc -c < /tmp/dl.bin) | starts with: $(head -c 4 /tmp/dl.bin | od -c | head -1)"

echo; echo "=== 7. CLIENT SHARES DOC WITH LAWYER ==="
curl -s -X POST $B/api/cases/c1/documents -H "$CA" -F "file=@sample_legal.pdf" -F "consent=true" | python3 -c "import sys,json;d=json.load(sys.stdin);print('  shared doc:', d['id'], d['fileName'], d['docType'])"

echo; echo "=== 8. ADMIN: DATASET UPLOAD + DOWNLOAD + SYSTEM REPORT ==="
echo "text,label" > /tmp/newds.csv
echo "sample annotation" >> /tmp/newds.csv
curl -s -X POST $B/api/admin/datasets -H "$AA" -F "file=@/tmp/newds.csv" -F "name=New annotated corpus" | j "d['name'] + ' (' + str(d['records']) + ' records)'"
DSID=$(curl -s $B/api/admin/datasets -H "$AA" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['id'])")
curl -s $B/api/admin/datasets/$DSID/download -H "$AA" -o /tmp/ds.bin && echo "  dataset download bytes: $(wc -c < /tmp/ds.bin)"
curl -s $B/api/admin/reports/system.csv -H "$AA" -o /tmp/sys.csv && echo "  system report lines: $(wc -l < /tmp/sys.csv)"

echo; echo "=== 9. REVOKE STILL WORKS PROPERLY (immediate) ==="
curl -s -X POST $B/api/admin/users/u3/revoke -H "$AA" | j "d['message']"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"elna@example.com","password":"client123"}')
echo "  elna login: HTTP $CODE (expect 403)"
echo "ALL NEW-FEATURE E2E TESTS DONE"
