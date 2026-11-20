#!/usr/bin/env bash
# ============================================================
#  LegalCase ERP - front-end launcher (macOS / Linux)
#
#  Usage:  ./run.sh                -> JavaFX client, DEMO mode (no backend)
#          ./run-connected.sh      -> backend + client talking over REST
#
#  Requires: JDK 21 (https://adoptium.net) and Maven on your PATH.
#  First run builds the project and downloads dependencies - be patient.
# ============================================================
set -e
cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java not found. Install JDK 21 from https://adoptium.net"; exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
    echo "[ERROR] Maven not found. Install Maven from https://maven.apache.org"; exit 1
fi

echo "== Building all modules (shared + backend + frontend) =="
mvn -q install -DskipTests

echo "== Starting the JavaFX client in DEMO mode =="
echo "   (log in with ally@mongalalaw.co.za / lawyer123 or thabo@example.com / client123)"
mvn -pl frontend org.openjfx:javafx-maven-plugin:0.0.8:run
