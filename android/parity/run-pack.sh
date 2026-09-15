#!/usr/bin/env bash
# Capture one parity state for one app on emulator-5554 with the Pack's capture.mjs.
#
#   ./run-pack.sh <rn|native> <state> [out-root]
#   state: base | dark | font2 | interactions | empty | error
#
# - Output: <out-root>/<state>/<label>/android/{home,thread,profile}.png and manifest.json (default out-root: out).
# - dark runs `cmd uimode night yes` first; font2 runs `settings put system font_scale 2.0` first. Both are reset on exit.
# - The seed and the capture run under /tmp/bsky-seed.lock (retry every 10 s, stale after 20 min).
set -uo pipefail
cd "$(dirname "$0")"
LABEL=$1; STATE=$2; OUT_ROOT=${3:-out}
SERIAL=emulator-5554
PACK=../../../../migration-pack/skills/check-parity/scripts
case $LABEL in
  rn) APP_ID=xyz.blueskyweb.app ;;
  native) APP_ID=io.nativestarter.showcase.bluesky ;;
  *) echo "label must be rn or native" >&2; exit 2 ;;
esac
case $STATE in
  base|dark|font2) FLOW=flows/pack/readonly.yml ;;
  interactions|empty|error) FLOW=flows/pack/$STATE.yml ;;
  *) echo "unknown state $STATE" >&2; exit 2 ;;
esac

reset_device() {
  adb -s $SERIAL shell cmd uimode night no >/dev/null 2>&1
  adb -s $SERIAL shell settings put system font_scale 1.0 >/dev/null 2>&1
}
until mkdir /tmp/bsky-seed.lock 2>/dev/null; do
  age=$(( $(date +%s) - $(stat -f %m /tmp/bsky-seed.lock 2>/dev/null || date +%s) ))
  if [ "$age" -gt 1200 ]; then rmdir /tmp/bsky-seed.lock 2>/dev/null || true; continue; fi
  sleep 10
done
trap 'reset_device; rmdir /tmp/bsky-seed.lock 2>/dev/null || true' EXIT

rm -rf "$OUT_ROOT/$STATE/$LABEL"
mkdir -p "$OUT_ROOT/$STATE"
case $STATE in
  dark) adb -s $SERIAL shell cmd uimode night yes >/dev/null; sleep 3 ;;
  font2) adb -s $SERIAL shell settings put system font_scale 2.0; sleep 3 ;;
esac
node $PACK/capture.mjs --flow "$FLOW" --platform android --device $SERIAL --label "$LABEL" \
  --out "$OUT_ROOT/$STATE" --env APP_ID=$APP_ID > "$OUT_ROOT/$STATE/$LABEL.log" 2>&1
rc=$?
echo "$(date +%H:%M:%S) $LABEL $STATE: capture.mjs exit $rc ($(ls "$OUT_ROOT/$STATE/$LABEL/android" 2>/dev/null | tr '\n' ' '))"
exit $rc
