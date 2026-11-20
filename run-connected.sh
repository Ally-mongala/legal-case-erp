#!/usr/bin/env bash
# ============================================================
#  LegalCase ERP - FULL SYSTEM (macOS / Linux)
#
#  Starts the backend REST API, waits for it to come up, then
#  launches the JavaFX client in CONNECTED mode. Closing the
#  client stops the backend too.
# ============================================================
set -e
cd "$(dirname "$0")"
mvn -q install -DskipTests

echo "== Starting backend =="
java -jar backend/target/legal-backend.jar > /tmp/legal-backend.log 2>&1 &
BACKEND_PID=$!
trap 'echo "Stopping backend..."; kill $BACKEND_PID 2>/dev/null' EXIT

for i in $(seq 1 40); do
    if curl -s http://localhost:${LCE_PORT:-8080}/api/health >/dev/null 2>&1; then
        echo "   backend online"
        break
    fi
    sleep 1
done

echo "== Starting JavaFX client (CONNECTED mode) =="
echo "   Log in with ally@mongalalaw.co.za / lawyer123 (lawyer) or thabo@example.com / client123 (client)"
LCE_BACKEND_URL=http://localhost:${LCE_PORT:-8080} \
    mvn -pl frontend org.openjfx:javafx-maven-plugin:0.0.8:run
