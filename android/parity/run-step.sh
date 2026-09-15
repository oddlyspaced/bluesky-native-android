#!/usr/bin/env bash
# One device capture with the foreground guard: ./run-step.sh <rn|native> <state> <out-root>
# Stops (exit 3) when the foreground app on emulator-5554 is not one of the two showcase apps or the launcher.
cd "$(dirname "$0")" || exit 2
top=$(adb -s emulator-5554 shell dumpsys activity activities | grep topResumedActivity)
echo "$(date +%H:%M:%S) foreground: ${top##*u0 }"
case "$top" in
  *io.nativestarter.showcase.bluesky/*|*xyz.blueskyweb.app/*|*launcher*) ;;
  *) echo "BLOCKED: another app is in the foreground"; exit 3 ;;
esac
./run-pack.sh "$@"
rc=$?
[ $rc -ne 0 ] && grep -iE "error|fail|assert" "$3/$2/$1.log" | tail -8
exit $rc
