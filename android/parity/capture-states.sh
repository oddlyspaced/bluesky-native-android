#!/usr/bin/env bash
# Capture the extra parity states for one app on emulator-5554:
#   interactions (like + repost), dark (night mode), font2 (font_scale 2.0), empty (no posts), error (reseeded session).
# Usage: capture-states.sh <app id> <prefix> [destination dir] [states...]
#   RN:     capture-states.sh xyz.blueskyweb.app rn-android ../../parity/baseline/rn
#   native: capture-states.sh io.nativestarter.showcase.bluesky native-android
# Each flow holds /tmp/bsky-seed.lock for its seed and capture. Device settings are always reset on exit.
# RN files are copied as android-<state>-<screen>.png and never overwrite an existing file.
set -uo pipefail
cd "$(dirname "$0")"
APP_ID=$1; PREFIX=$2; DEST=${3:-}; shift $(( $# >= 3 ? 3 : $# ))
STATES=${*:-interactions dark font2 empty error}
SERIAL=emulator-5554
mkdir -p logs shots

reset_device() {
  adb -s $SERIAL shell cmd uimode night no >/dev/null 2>&1
  adb -s $SERIAL shell settings put system font_scale 1.0 >/dev/null 2>&1
}
trap 'reset_device; rmdir /tmp/bsky-seed.lock 2>/dev/null || true' EXIT

acquire() {
  while ! mkdir /tmp/bsky-seed.lock 2>/dev/null; do
    local age=$(( $(date +%s) - $(stat -f %m /tmp/bsky-seed.lock 2>/dev/null || date +%s) ))
    if [ "$age" -gt 1200 ]; then rmdir /tmp/bsky-seed.lock 2>/dev/null || true; continue; fi
    sleep 10
  done
}

run_flow() { # flow state
  local flow=$1 state=$2
  acquire
  maestro --device $SERIAL test -e APP_ID=$APP_ID -e PREFIX=$PREFIX-$state flows/$flow > logs/$PREFIX-$state.log 2>&1
  local rc=$?
  rmdir /tmp/bsky-seed.lock
  echo "$(date +%H:%M:%S) $PREFIX $state: maestro exit $rc"
}

adb -s $SERIAL logcat -c
for state in $STATES; do
  case $state in
    interactions) run_flow interactions.yml interactions ;;
    dark) adb -s $SERIAL shell cmd uimode night yes; sleep 3; run_flow readonly.yml dark; adb -s $SERIAL shell cmd uimode night no; sleep 2 ;;
    font2) adb -s $SERIAL shell settings put system font_scale 2.0; sleep 3; run_flow readonly.yml font2; adb -s $SERIAL shell settings put system font_scale 1.0; sleep 2 ;;
    empty) run_flow empty.yml empty ;;
    error) run_flow error.yml error ;;
  esac
done
echo "metric lines in logcat for this run: $(adb -s $SERIAL logcat -d 2>/dev/null | grep -c '(metric)')" | tee logs/$PREFIX-metric-lines.txt

if [ -n "$DEST" ]; then
  mkdir -p "$DEST"
  for f in shots/$PREFIX-*.png; do
    [ -e "$f" ] || continue
    base=$(basename "$f" .png); rest=${base#$PREFIX-}; state=${rest%%-*}; screen=${rest#*-}
    target="$DEST/android-$state-$screen.png"
    if [ -e "$target" ]; then echo "kept existing $target"; else cp "$f" "$target"; echo "saved $target"; fi
  done
fi
