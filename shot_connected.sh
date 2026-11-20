#!/bin/bash
# Screenshot harness - CONNECTED mode (front-end talks to the live backend).
set -u
cd /home/user/legal-case-erp
export DISPLAY=:99
CP="frontend/target/classes:shared/target/classes:$(cat /tmp/frontend-cp.txt)"
OUT=/home/user/legal-case-erp/screenshots
mkdir -p "$OUT"

# arg1=shot name, arg2=demo view, arg3=email, arg4=password
shot() {
  local name="$1" view="$2" email="$3" pw="$4"
  Xvfb :99 -screen 0 1280x800x24 -nolisten tcp >/dev/null 2>&1 &
  local xvfb_pid=$!
  sleep 1.5

  local args=(-Dprism.order=sw -Ddemo.view="$view" -Ddemo.user="$email" -Ddemo.password="$pw")
  LCE_BACKEND_URL=http://localhost:8080 \
    java "${args[@]}" -cp "$CP" za.co.mongala.legal.MainLauncher >"/tmp/app_${name}.log" 2>&1 &
  local app_pid=$!
  sleep 9

  import -display :99 -window root "$OUT/${name}.png" 2>/dev/null \
    || xwd -display :99 -root | convert xwd:- "png:$OUT/${name}.png" 2>/dev/null \
    || echo "WARN: screenshot failed for $name"

  local errs
  errs=$(grep -E "Exception|Caused by" "/tmp/app_${name}.log" | head -4)
  if [ -n "$errs" ]; then echo "=== $name errors ==="; echo "$errs"; else echo "OK: $name"; fi

  kill $app_pid 2>/dev/null; wait $app_pid 2>/dev/null
  kill $xvfb_pid 2>/dev/null; wait $xvfb_pid 2>/dev/null
  sleep 0.5
}

LAW=ally@mongalalaw.co.za
CLI=thabo@example.com

shot login_connected     ""             "$LAW" "lawyer123"
shot dashboard_connected "dashboard"    "$LAW" "lawyer123"
shot cases_connected     "cases"        "$LAW" "lawyer123"
shot case_detail_connected "case_detail" "$LAW" "lawyer123"
shot calendar_connected  "calendar"     "$LAW" "lawyer123"
shot activity_connected  "activity"     "$LAW" "lawyer123"
shot client_home_connected "client_home" "$CLI" "client123"
shot client_messages_connected "client_messages" "$CLI" "client123"

echo "---- done ----"
ls -la "$OUT" | grep connected
