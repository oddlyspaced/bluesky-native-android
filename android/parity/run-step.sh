#!/usr/bin/env bash
# One device capture with guards: ./run-step.sh <rn|native> <state> <out-root>
# Exit 3 when the foreground app on emulator-5554 is not a showcase app or the launcher.
# Exit 4 when a mobilecli DeviceServer process or its adb forward is present (it holds UiAutomation).
cd "$(dirname "$0")" || exit 2
srv=$(adb -s emulator-5554 shell ps -A | grep -iE "mobilecli|DeviceServer")
# `forward --list` prints the forwards of every attached device, so keep only emulator-5554 lines.
fwd=$(adb -s emulator-5554 forward --list | grep "^emulator-5554 " | grep -i mobilecli)
if [ -n "$srv$fwd" ]; then
  echo "$(date +%H:%M:%S) BLOCKED: DeviceServer present: $srv $fwd"
  exit 4
fi
top=$(adb -s emulator-5554 shell dumpsys activity activities | grep topResumedActivity)
echo "$(date +%H:%M:%S) foreground: ${top##*u0 }"
case "$top" in
  *io.nativestarter.showcase.bluesky/*|*xyz.blueskyweb.app/*|*launcher*) ;;
  *) echo "BLOCKED: another app is in the foreground"; exit 3 ;;
esac
./run-pack.sh "$@"
rc=$?
[ $rc -ne 0 ] && grep -vE "^\s+at " "$3/$2/$1.log" | grep -iE "error|fail|assert|did not" | tail -6
exit $rc
