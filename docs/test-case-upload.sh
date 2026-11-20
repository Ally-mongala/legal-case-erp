#!/bin/bash
# ============================================================================
#  Test-case uploader - uploads each generated test document and reports the
#  AI outcome (PROCESSED / BLOCKED / FAILED) so you can verify the system.
#
#  Usage:   bash test-case-upload.sh [base-url]
#  Default: http://localhost:8080
#
#  It logs in as the lawyer, creates a fresh test case per scenario, uploads
#  the document, waits for AI processing, and prints EXPECTED vs ACTUAL.
# ============================================================================
set -u
B="${1:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")" && pwd)/test-cases"

TOKEN=$(curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"ally@mongalalaw.co.za","password":"lawyer123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
AUTH="Authorization: Bearer $TOKEN"

pass=0; fail=0

check() {
  local label="$1" file="$2" caseId="$3" expected="$4"
  local doc st reason
  doc=$(curl -s -X POST "$B/api/cases/$caseId/documents" -H "$AUTH" \
        -F "file=@$file" -F "consent=true" \
        | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
  for i in $(seq 1 30); do
    st=$(curl -s "$B/api/documents/$doc" -H "$AUTH" \
         | python3 -c "import sys,json;print(json.load(sys.stdin)['processingStatus'])")
    [ "$st" != "PROCESSING" ] && break
    sleep 1
  done
  reason=$(curl -s "$B/api/documents/$doc" -H "$AUTH" \
           | python3 -c "import sys,json;print(json.load(sys.stdin).get('rejectionReason') or '')")
  printf "  %-22s EXPECT %-10s ACTUAL %-10s" "$label" "$expected" "$st"
  if [ "$st" = "$expected" ]; then echo "  ✓"; pass=$((pass+1));
  else echo "  ✗  ($reason)"; fail=$((fail+1)); fi
  [ -n "$reason" ] && echo "        reason: $reason"
}

echo "=================================================================="
echo "  LegalCase ERP - test-case uploader  (backend: $B)"
echo "=================================================================="

# ---- 1. MATCHING documents -> PROCESSED ----
echo; echo ">>> Scenario 1: documents that MATCH their case -> PROCESSED"
C1=$(curl -s -X POST "$B/api/cases" -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"title":"Test - Mokoena v Siyaya (match)","clientName":"Thabo Mokoena","opposingParty":"Siyaya Transport (Pty) Ltd","matterType":"Personal Injury","court":"Johannesburg High Court"}' \
     | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
check "match mokoena.pdf" "$DIR/1-match_mokoena_affidavit.pdf" "$C1" "PROCESSED"

C2=$(curl -s -X POST "$B/api/cases" -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"title":"Test - Van Wyk v Standard Bank (match)","clientName":"Elna van Wyk","opposingParty":"Standard Bank of SA Ltd","matterType":"Consumer Credit","court":"Pretoria Magistrate'"'"'s Court"}' \
     | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
check "match vanwyk.docx" "$DIR/1-match_vanwyk_statement.docx" "$C2" "PROCESSED"

# ---- 2. WRONG MATTER documents -> BLOCKED (REJECTED) ----
echo; echo ">>> Scenario 2: documents about a DIFFERENT matter -> BLOCKED"
check "wrong nkosi.pdf"   "$DIR/2-wrong_matter_nkosi_abba.pdf"  "$C1" "REJECTED"
check "wrong divorce.docx" "$DIR/2-wrong_matter_divorce.docx"    "$C1" "REJECTED"

# ---- 3. NO CASE INFO documents -> BLOCKED ----
echo; echo ">>> Scenario 3: documents with NO case information -> BLOCKED"
check "no-info grocery.pdf"   "$DIR/3-no_case_info_grocery_list.pdf"   "$C1" "REJECTED"
check "no-info utility.docx"  "$DIR/3-no_case_info_utility_bill.docx"  "$C1" "REJECTED"

# ---- 4. PARTIAL MATCH -> PROCESSED (at least one party matches) ----
echo; echo ">>> Scenario 4: same client but different defendant -> PROCESSED (party overlap)"
check "partial match.pdf" "$DIR/4-partial_match_wrong_defendant.pdf" "$C1" "PROCESSED"

# ---- 5. BLANK / scanned doc -> FAILED ----
echo; echo ">>> Scenario 5: blank/scanned document (no extractable text) -> FAILED"
check "scanned no-text.pdf" "$DIR/5-scanned_no_text.pdf" "$C1" "FAILED"

echo; echo "=================================================================="
echo "  RESULT: $pass passed, $fail failed"
echo "=================================================================="
exit $fail
