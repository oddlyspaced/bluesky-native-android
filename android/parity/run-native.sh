#!/usr/bin/env bash
# Build, install and capture the native Android app, then compare with the RN Android baselines.
#
# Shared-machine rules:
# - Gradle runs only while holding /tmp/native-build.lock (another agent runs xcodebuild).
# - Seeding and the Maestro capture run only while holding /tmp/bsky-seed.lock (the seed resets shared data).
# - A lock older than its stale limit (build 30 min, seed 20 min) is removed and retaken.
#
# Usage: android/parity/run-native.sh [flow.yml] [fuzz]
# The script stops at the first failure, so a failed build never produces numbers from an older install.
set -euo pipefail
cd "$(dirname "$0")"
FLOW="${1:-capture-native.yml}"
FUZZ="${2:-5%}"
SERIAL=emulator-5554

acquire() { # lock wait_seconds stale_seconds
  local lock=$1 wait=$2 stale=$3
  while ! mkdir "$lock" 2>/dev/null; do
    local age=$(( $(date +%s) - $(stat -f %m "$lock" 2>/dev/null || date +%s) ))
    if [ "$age" -gt "$stale" ]; then rmdir "$lock" 2>/dev/null || true; continue; fi
    sleep "$wait"
  done
}

acquire /tmp/native-build.lock 15 1800
trap 'rmdir /tmp/native-build.lock 2>/dev/null || true' EXIT
(cd .. && ANDROID_SERIAL=$SERIAL ./gradlew --no-daemon :app:installDebug)
rmdir /tmp/native-build.lock
trap - EXIT

acquire /tmp/bsky-seed.lock 10 1200
trap 'rmdir /tmp/bsky-seed.lock 2>/dev/null || true' EXIT
maestro --device "$SERIAL" test -e PLATFORM=android "$FLOW"
rmdir /tmp/bsky-seed.lock
trap - EXIT

./compare.sh "$FUZZ"
