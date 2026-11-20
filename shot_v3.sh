#!/bin/bash
set -u
cd /home/user/legal-case-erp
export DISPLAY=:99
CP="frontend/target/classes:shared/target/classes:$(cat /tmp/frontend-cp.txt)"
OUT=screenshots; mkdir -p "$OUT"
shot() {
  local name="$1" view="$2" email="$3" pw="$4"
  Xvfb :99 -screen 0 1280x800x24 -nolisten tcp >/dev/null 2>&1 &
  local xvfb_pid=$!
  sleep 1.5
  local args=(-Dprism.order=sw)
  [ -n "$view" ] && args+=(-Ddemo.view="$view" -Ddemo.user="$email" -Ddemo.password="$pw")
  LCE_BACKEND_URL=http://localhost:8080 java "${args[@]}" -cp "$CP" za.co.mongala.legal.MainLauncher >"/tmp/app_${name}.log" 2>&1 &
  local app_pid=$!
  sleep 9
  import -display :99 -window root "$OUT/${name}.png" 2>/dev/null || echo "shot failed $name"
  local errs; errs=$(grep -E "Exception|Caused by" "/tmp/app_${name}.log" | head -3)
  if [ -n "$errs" ]; then echo "=== $name errors ==="; echo "$errs"; else echo "OK: $name"; fi
  kill $app_pid 2>/dev/null; wait $app_pid 2>/dev/null
  kill $xvfb_pid 2>/dev/null; wait $xvfb_pid 2>/dev/null
  sleep 0.5
}
LAW=ally@mongalalaw.co.za; CLI=thabo@example.com; ADM=admin@mongalalaw.co.za
shot login_v3            ""                "$LAW" "lawyer123"
shot case_detail_v3      "case_detail"     "$LAW" "lawyer123"
shot calendar_v3         "calendar"        "$LAW" "lawyer123"
shot lawyer_ratings_v3   "ratings"         "$LAW" "lawyer123"
shot activity_v3         "activity"        "$LAW" "lawyer123"
shot client_documents_v3 "client_documents" "$CLI" "client123"
shot admin_system_v3     "admin_system"    "$ADM" "admin123"
echo done
