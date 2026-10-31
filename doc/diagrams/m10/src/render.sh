#!/usr/bin/env bash
# render.sh [figure-id ...]   - rebuild SVGs, rasterise with Edge, trim margins.
set -u
S="$(cd "$(dirname "$0")" && pwd)"
M=$(cygpath -m "$S")
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
mkdir -p "$S/bw" "$S/bw-raw" "$S/bw-png"

python "$S/build_figures.py" || exit 1

ids=("$@")
if [ ${#ids[@]} -eq 0 ]; then
  ids=()
  for f in "$S"/bw/*.svg; do ids+=("$(basename "$f" .svg)"); done
fi

for id in "${ids[@]}"; do
  cat > "$S/bw/$id.html" <<HTML
<!doctype html><meta charset="utf-8">
<style>html,body{margin:0;background:#fff}img{display:block}</style>
<img src="$id.svg">
HTML
  # window at least as large as the drawing, so nothing is cut off
  read -r WW WH < <(python -c "import re,sys; t=open(sys.argv[1],encoding='utf-8').read(400); \
m=re.search(r'width=\"(\d+)\" height=\"(\d+)\"', t); print(max(1900,int(m[1])+60), max(1700,int(m[2])+60))" \
    "$S/bw/$id.svg")
  "$EDGE" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
    --window-size=$WW,$WH --virtual-time-budget=12000 \
    --user-data-dir="$M/p" --screenshot="$M/bw-raw/$id.png" "file:///$M/bw/$id.html" >/dev/null 2>&1
  rm -rf "$S/p"
done

uv run --no-project --with pillow python "$S/trim.py" "${ids[@]}"
