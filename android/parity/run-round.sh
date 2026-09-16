#!/usr/bin/env bash
# Captures one parity state end to end and writes its committed evidence (reviews A2 and A9).
#
#   ./run-round.sh <state> [out-root]
#   state: base | dark | font2 | interactions | empty | error
#
# Four captures per state, each under its own hold of /tmp/bsky-seed.lock, each reseeding through its flow:
#
#   rn        the React Native build              -> the diff's RN side
#   rn-r2     the same build again                -> the RN noise floor for THIS state
#   native    the Compose build                   -> the diff's native side
#   native-r2 the same build again                -> the native noise floor for THIS state
#
# Then collect-evidence.sh measures the masks on both images, runs diff.mjs raw and masked, diffs each pair of
# repeat runs for the noise floor, and compares the event logs. The numbers printed at the end are the ones
# PARITY.md quotes; the raw PNGs stay out of git.
#
# Both apps are captured in the same batch, on the same build, so "same run" holds for the diff AND the noise.
set -uo pipefail
cd "$(dirname "$0")"

STATE=${1:?usage: run-round.sh <state> [out-root]}
ROOT=${2:-out}

for label in rn rn-r2 native native-r2; do
  echo "=== $STATE / $label  $(date +%H:%M:%S)"
  ./run-step.sh "$label" "$STATE" "$ROOT"
  rc=$?
  if [ $rc -ne 0 ]; then
    echo "$STATE / $label failed (exit $rc). Stopping this state so a half-captured round is never measured." >&2
    exit $rc
  fi
done

# The RN app has no file sink; its events come from the device log of its own capture (review A4, A6).
for side in rn native; do
  log="$ROOT/$STATE/$side-logcat.txt"
  [ -f "$log" ] || continue
  if [ "$side" = rn ]; then
    node rn-events.mjs "$log" --out "$ROOT/$STATE/rn-events.jsonl" || true
  fi
done

./collect-evidence.sh "$STATE" "$ROOT"
