#!/usr/bin/env bash
# ============================================================
#  LegalCase ERP - backend REST API launcher (macOS / Linux)
#
#  Starts the full backend: JWT auth, H2 database (./data),
#  PDF text extraction, NLP pipeline, calendar sync, audit log.
#  Default port 8080 (override with LCE_PORT).
#
#  Optional Google Calendar API (real events):
#    LCE_GOOGLE_CAL_ID=primary \
#    LCE_GOOGLE_REFRESH_TOKEN=... \
#    LCE_GOOGLE_CLIENT_ID=... \
#    LCE_GOOGLE_CLIENT_SECRET=... \
#    ./run-backend.sh
# ============================================================
set -e
cd "$(dirname "$0")"
mvn -q clean install -DskipTests
echo "== Starting backend on http://localhost:${LCE_PORT:-8080} =="
echo "   Open http://localhost:${LCE_PORT:-8080} in your browser for the web client"
java -jar backend/target/legal-backend.jar
