#!/usr/bin/env bash
# Compare native Android captures with the RN Android baselines (same device, 1280x2856).
# Masks are applied identically to both images. Pixel coordinates come from the RN baselines.
#
#   dynamic : status bar, relative time text, anchor timestamp        (always masked)
#   logo    : header mark (licensing, accepted difference)             (always masked)
#   icons   : UI glyph regions that use Lucide instead of upstream     (second measurement only)
#
# Usage: ./compare.sh [fuzz]   (default fuzz 5%)
# Output: one line per screen and mask set. `magick compare` exits 1 when images differ; that is not an error.
set -u
cd "$(dirname "$0")"
FUZZ="${1:-5%}"
# RN_DIR holds android-<screen>.png. Set it to a folder of an earlier native run to measure the noise floor.
RN="${RN_DIR:-../../parity/baseline/rn}"
NATIVE=shots
OUT="${OUT_DIR:-diff}"
mkdir -p "$OUT"

STATUS="rectangle 0,0 1280,150"
BOTTOM_BAR="rectangle 0,2640 1280,2800"

dynamic_home="$STATUS
rectangle 1060,630 1280,705
rectangle 1060,880 1280,950
rectangle 1060,1150 1280,1225
rectangle 1060,1420 1280,1495
rectangle 1060,1695 1280,1765"
logo_home="rectangle 570,165 710,280"
icons_home="rectangle 30,180 140,270
rectangle 1130,180 1250,270
rectangle 1030,475 1250,570
rectangle 200,785 1250,860
rectangle 200,1030 1250,1105
rectangle 200,1300 1250,1375
rectangle 200,1570 1250,1645
rectangle 200,1840 1250,1915
rectangle 60,1975 190,2115
rectangle 860,2480 940,2570
rectangle 1070,2460 1180,2570
$BOTTOM_BAR"

dynamic_thread="$STATUS
rectangle 30,600 500,675
rectangle 1060,815 1280,890"
logo_thread=""
icons_thread="rectangle 30,180 140,270
rectangle 1120,180 1250,270
rectangle 480,600 570,675
rectangle 30,670 1250,750
rectangle 200,965 1250,1040
$BOTTOM_BAR"

dynamic_profile="$STATUS
rectangle 1060,1120 1280,1195"
logo_profile=""
icons_profile="rectangle 1010,485 1250,590
rectangle 200,1270 1250,1345
rectangle 1070,2460 1180,2570
$BOTTOM_BAR"

mask() { # in out rects
  local draw=()
  while IFS= read -r r; do [ -n "$r" ] && draw+=(-draw "$r"); done <<< "$3"
  magick "$1" -fill black "${draw[@]}" "$2"
}

total=$((1280 * 2856))
for s in home thread profile; do
  dyn_var="dynamic_$s"; logo_var="logo_$s"; icon_var="icons_$s"
  base="${!dyn_var}
${!logo_var}"
  full="$base
${!icon_var}"
  for set in base full; do
    rects="${!set}"
    mask "$RN/android-$s.png" "$OUT/rn-$s-$set.png" "$rects"
    mask "$NATIVE/native-android-$s.png" "$OUT/native-$s-$set.png" "$rects"
    px=$(magick compare -metric AE -fuzz "$FUZZ" "$OUT/rn-$s-$set.png" "$OUT/native-$s-$set.png" "$OUT/diff-$s-$set.png" 2>&1 >/dev/null | cut -d' ' -f1)
    label=$([ "$set" = base ] && echo "dynamic+logo masked" || echo "dynamic+logo+icons masked")
    pct=$(python3 -c "print(f'{100*$px/$total:.2f}')")
    echo "$s | $label | fuzz $FUZZ | $px px | $pct%"
  done
done
