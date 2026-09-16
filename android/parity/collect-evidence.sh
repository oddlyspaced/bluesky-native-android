#!/usr/bin/env bash
# Turns one captured state into the committed evidence for PARITY.md (review A2).
#
#   ./collect-evidence.sh <state> <out-root> [evidence-dir]
#
# Reads the capture that run-step.sh produced:
#   <out-root>/<state>/rn/android/{home,thread,profile}.png
#   <out-root>/<state>/native/android/...
#   <out-root>/<state>/native-events.jsonl   (native capture, when the flow drives events)
#   <out-root>/<state>/rn-events.jsonl       (RN capture, when the flow drives events)
#
# Writes, into <evidence-dir>/<state>/ (default parity/evidence/<state>/):
#   masks.json                 the masks make_masks.py measured on both images
#   diff-raw-results.json      diff.mjs with no masks
#   diff-masked-results.json   diff.mjs with the masks
#   native-events.jsonl        the native event log, as read off the device
#   rn-events.jsonl            the RN event log for the same capture
#   events-ordered.txt         events.mjs --ordered output
#   events-result.json         events.mjs machine-readable result
#   noise-<label>-results.json per-state noise floor, when a second run of one app is present (review A9)
#
# The PNGs stay out of git; only the RN baselines under parity/baseline/rn/ are committed.
# make_masks.py failing is a real result, not a hiccup: it means a band would hide the element under test.
set -uo pipefail
cd "$(dirname "$0")"

STATE=${1:?usage: collect-evidence.sh <state> <out-root> [evidence-dir]}
ROOT=${2:?usage: collect-evidence.sh <state> <out-root> [evidence-dir]}
EV=${3:-evidence}/$STATE
PACK=../../../../migration-pack/skills/check-parity/scripts

RN_DIR="$ROOT/$STATE/rn/android"
NAT_DIR="$ROOT/$STATE/native/android"
for d in "$RN_DIR" "$NAT_DIR"; do
  [ -d "$d" ] || { echo "missing capture: $d" >&2; exit 2; }
done
mkdir -p "$EV"

echo "== masks ($STATE)"
if ! python3 make_masks.py masks-spec.json "$STATE" "$RN_DIR" "$NAT_DIR" "$ROOT/masks-$STATE.json"; then
  echo "make_masks.py failed for $STATE: a declared band does not match this state. Fix the bands." >&2
  exit 3
fi
cp "$ROOT/masks-$STATE.json" "$EV/masks.json"

echo "== diff raw ($STATE)"
node "$PACK/diff.mjs" --rn "$RN_DIR" --native "$NAT_DIR" --out "$ROOT/$STATE/diff-raw"
echo "== diff masked ($STATE)"
node "$PACK/diff.mjs" --rn "$RN_DIR" --native "$NAT_DIR" --out "$ROOT/$STATE/diff-masked" \
  --mask "$ROOT/masks-$STATE.json"
cp "$ROOT/$STATE/diff-raw/results.json" "$EV/diff-raw-results.json"
cp "$ROOT/$STATE/diff-masked/results.json" "$EV/diff-masked-results.json"

# Per-state noise floor (review A9): the same app captured twice for THIS state, with THIS state's masks,
# in the same batch and on the same build. run-step.sh writes the second run as <label>-r2.
for label in native rn; do
  second="$ROOT/$STATE/$label-r2/android"
  first="$ROOT/$STATE/$label/android"
  [ -d "$second" ] || continue
  echo "== noise floor $label ($STATE)"
  node "$PACK/diff.mjs" --rn "$first" --native "$second" --out "$ROOT/$STATE/noise-$label" \
    --mask "$ROOT/masks-$STATE.json"
  cp "$ROOT/$STATE/noise-$label/results.json" "$EV/noise-$label-results.json"
done

# Event logs, for the flows that drive events. rn-events-all.jsonl is every event the RN app logged;
# rn-events.jsonl is the slice's events, which is what the comparison runs on.
for src_name in native-events.jsonl rn-events.jsonl rn-events-all.jsonl; do
  src="$ROOT/$STATE/$src_name"
  [ -f "$src" ] && cp "$src" "$EV/$src_name"
done
if [ -f "$EV/native-events.jsonl" ] && [ -f "$EV/rn-events.jsonl" ]; then
  echo "== events ($STATE)"
  # Every capture reseeds, so alice's DID differs between the RN run and the native run. An author feed
  # descriptor embeds it ("author|<did>|posts_and_author_threads"), which makes it capture-volatile in the
  # same way `uri` and `authorDid` are. Normalising the DID keeps the descriptor's shape under comparison,
  # where adding `feedDescriptor` to --ignore would also stop checking the Following feed's descriptor.
  # The unnormalised logs stay next to these as the evidence.
  for side in rn native; do
    sed -E 's/did:plc:[a-z0-9]{20,}/did:plc:SEEDED/g' "$EV/$side-events.jsonl" > "$EV/$side-events-cmp.jsonl"
  done
  node "$PACK/events.mjs" --rn "$EV/rn-events-cmp.jsonl" --native "$EV/native-events-cmp.jsonl" \
    --ignore uri,authorDid,initMs,likerClout,postClout --ordered \
    --out "$EV/events-result.json" | tee "$EV/events-ordered.txt"
fi

echo "== summary ($STATE)"
python3 - "$EV" <<'PY'
import json, sys, pathlib
ev = pathlib.Path(sys.argv[1])
for name in ("diff-raw-results.json", "diff-masked-results.json", "noise-native-results.json", "noise-rn-results.json"):
    p = ev / name
    if not p.exists():
        continue
    data = json.loads(p.read_text())
    for s in data["screens"]:
        ratio = "-" if s["ratio"] is None else f"{s['ratio'] * 100:.2f}%"
        px = "-" if s["diffPixels"] is None else f"{s['diffPixels']:,}"
        print(f"  {name:28} {s['name']:8} {px:>10} px  {ratio:>7}  {s['status']}")
PY
echo "evidence -> $EV"
