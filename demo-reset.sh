#!/usr/bin/env bash
# =============================================================================
#  demo-reset.sh  -  return the system to a clean, freshly seeded state
#
#  Use this if the database is in an unexpected state during rehearsal or
#  before you present. It DELETES ./data (the H2 database and the encrypted
#  documents) and the data is recreated on the next start.
#
#  Usage:   bash demo-reset.sh             (stop, wipe, restart)
#           bash demo-reset.sh --wipe-only (stop and wipe, do not restart)
# =============================================================================
set -u
cd "$(dirname "$0")"
WIPE_ONLY="${1:-}"

echo
echo "== 1/3  Stopping any running backend =="
if pkill -f legal-backend.jar 2>/dev/null; then
  echo "   Backend stopped."
  sleep 2
else
  echo "   No backend process found (that is fine)."
fi

echo
echo "== 2/3  Deleting the data folder =="
if [ -d data ]; then
  rm -rf data
  echo "   ./data removed - the database and uploaded documents are gone."
else
  echo "   ./data did not exist."
fi

if [ "$WIPE_ONLY" = "--wipe-only" ]; then
  echo
  echo "Done. Start the backend when you are ready:"
  echo "   java -jar backend/target/legal-backend.jar"
  exit 0
fi

echo
echo "== 3/3  Starting the backend (it will reseed automatically) =="
if [ ! -f backend/target/legal-backend.jar ]; then
  echo "   ERROR: backend/target/legal-backend.jar not found."
  echo "   Build it first:  mvn clean install -DskipTests"
  exit 1
fi
echo "   Starting... open http://localhost:8080 once it says 'Listening on'"
exec java -jar backend/target/legal-backend.jar
