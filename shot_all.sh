#!/bin/bash
# Screenshot harness for the JavaFX app (headless validation + visuals)
set -u
cd /home/user/legal-case-erp
export DISPLAY=:99
CP="target/classes:$(cat /tmp/cp.txt)"
OUT=/home/user/legal-case-erp/screenshots
mkdir -p "$OUT"

# arg1=shot name, arg2=demo view key, arg3=demo user email
shot() {
  local name="$1" view="$2" user="$3"
  Xvfb :99 -screen 0 1280x800x24 -nolisten tcp >/dev/null 2>&1 &
  local xvfb_pid=$!
  sleep 1.5

  local jvm_args=(-Dprism.order=sw -Dprism.text=t2k -Djava.awt.headless=true)
  [ -n "$view" ] && jvm_args+=(-Ddemo.view="$view")
  [ -n "$user" ] && jvm_args+=(-Ddemo.user="$user")

  java "${jvm_args[@]}" -cp "$CP" za.co.mongala.legal.MainLauncher >"/tmp/app_${name}.log" 2>&1 &
  local app_pid=$!
  sleep 10

  import -display :99 -window root "$OUT/${name}.png" 2>/dev/null \
    || xwd -display :99 -root | convert xwd:- "png:$OUT/${name}.png" 2>/dev/null \
    || echo "WARN: screenshot failed for $name"

  # report runtime exceptions from the app log
  local errs
  errs=$(grep -E "Exception|ERROR|Caused by" "/tmp/app_${name}.log" | head -5)
  if [ -n "$errs" ]; then
    echo "=== $name had errors ==="
    echo "$errs"
  else
    echo "OK: $name"
  fi

  kill $app_pid 2>/dev/null; wait $app_pid 2>/dev/null
  kill $xvfb_pid 2>/dev/null; wait $xvfb_pid 2>/dev/null
  sleep 0.5
}

LAW=ally@mongalalaw.co.za
CLI=thabo@example.com

shot login      ""          ""
shot dashboard  "dashboard" "$LAW"
shot cases      "cases"     "$LAW"
shot case_detail "case_detail" "$LAW"
shot calendar   "calendar"  "$LAW"
shot activity   "activity"  "$LAW"
shot client_home "client_home" "$CLI"
shot client_documents "client_documents" "$CLI"
shot client_messages "client_messages" "$CLI"

echo "---- done ----"
ls -la "$OUT"
