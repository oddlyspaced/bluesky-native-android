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
# `<label>-r2` is a second run of the same app, for the same state, in the same batch and on the same build.
# It is the per-state noise floor (review A9): two runs that should be identical, measured with that state's
# own masks, so a drift number can be read against the noise of that state rather than of `base`.
case $LABEL in
  rn|rn-r2) APP_ID=xyz.blueskyweb.app ;;
  native|native-r2) APP_ID=io.nativestarter.showcase.bluesky ;;
  *) echo "label must be rn, native, rn-r2 or native-r2" >&2; exit 2 ;;
esac

# The Pack's capture.mjs accepts only `rn` and `native` for --label, and it is the Pack's script, not ours, so a
# repeat run captures under its real label into a scratch directory and is moved into place afterwards.
BASE_LABEL=${LABEL%-r2}
if [ "$LABEL" = "$BASE_LABEL" ]; then
  CAPTURE_OUT="$OUT_ROOT/$STATE"
else
  CAPTURE_OUT="$OUT_ROOT/$STATE/.repeat"
fi
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

rm -rf "$OUT_ROOT/$STATE/$LABEL" "$OUT_ROOT/$STATE/.repeat"
mkdir -p "$CAPTURE_OUT"
case $STATE in
  dark) adb -s $SERIAL shell cmd uimode night yes >/dev/null; sleep 3 ;;
  font2) adb -s $SERIAL shell settings put system font_scale 2.0; sleep 3 ;;
esac
# Start each capture from an empty log, so an event log covers this capture and nothing else (review A2).
adb -s $SERIAL logcat -c >/dev/null 2>&1

# Every capture gets a hard time limit. A stuck Maestro flow holds the seed lock, which blocks this agent and
# the iOS one; one once held it for eight hours. On timeout the capture's own process tree is killed - only its
# descendants, never another agent's Maestro.
CAPTURE_TIMEOUT=${CAPTURE_TIMEOUT:-420}
kill_tree() {
  local p=$1 c
  for c in $(pgrep -P "$p" 2>/dev/null); do kill_tree "$c"; done
  kill -9 "$p" 2>/dev/null
}

node $PACK/capture.mjs --flow "$FLOW" --platform android --device $SERIAL --label "$BASE_LABEL" \
  --out "$CAPTURE_OUT" --env APP_ID=$APP_ID > "$OUT_ROOT/$STATE/$LABEL.log" 2>&1 &
cap_pid=$!
( sleep "$CAPTURE_TIMEOUT"; kill -0 "$cap_pid" 2>/dev/null && {
    echo "$(date +%H:%M:%S) $LABEL $STATE: TIMEOUT after ${CAPTURE_TIMEOUT}s, killing the capture" >&2
    kill_tree "$cap_pid"
  } ) &
watchdog=$!
wait "$cap_pid"
rc=$?
kill "$watchdog" 2>/dev/null

# Move a repeat run to <state>/<label>-r2/android, which is where collect-evidence.sh looks for it.
if [ "$LABEL" != "$BASE_LABEL" ] && [ -d "$CAPTURE_OUT/$BASE_LABEL/android" ]; then
  mkdir -p "$OUT_ROOT/$STATE/$LABEL"
  mv "$CAPTURE_OUT/$BASE_LABEL/android" "$OUT_ROOT/$STATE/$LABEL/android"
  rm -rf "$CAPTURE_OUT"
fi

# Event logs, read right after the capture that produced them. The native app appends RN-named events to
# files/events.jsonl in debug builds; `launchApp clearState` deletes it, so the file holds this capture only.
if [ "$LABEL" = native ]; then
  adb -s $SERIAL shell run-as io.nativestarter.showcase.bluesky cat files/events.jsonl \
    > "$OUT_ROOT/$STATE/native-events.jsonl" 2>/dev/null
  echo "$(date +%H:%M:%S) native $STATE: $(wc -l < "$OUT_ROOT/$STATE/native-events.jsonl" | tr -d ' ') event(s)"
fi
# The RN app has no file sink: its `ax.metric` calls reach the Metric logger, which the console transport
# prints. Keep the raw device log for this capture; rn-events.jsonl is derived from it by rn-events.mjs.
adb -s $SERIAL logcat -d > "$OUT_ROOT/$STATE/$LABEL-logcat.txt" 2>/dev/null

echo "$(date +%H:%M:%S) $LABEL $STATE: capture.mjs exit $rc ($(ls "$OUT_ROOT/$STATE/$LABEL/android" 2>/dev/null | tr '\n' ' '))"
exit $rc
