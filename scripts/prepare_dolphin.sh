#!/usr/bin/env bash
# Fetches the pinned Dolphin release, applies the CODBlackOps-Mobile integration patch and copies
# the launcher sources/resources/tests into Dolphin's Android app module.
#
#   scripts/prepare_dolphin.sh [destination]      (default: build/dolphin)
#
# Afterwards build with:  cd <destination>/Source/Android && ./gradlew :app:assembleRelease
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/dolphin.version"
DEST="${1:-$ROOT/build/dolphin}"

if [ ! -d "$DEST/.git" ]; then
  echo "==> Cloning Dolphin $DOLPHIN_TAG"
  git clone --depth 1 --branch "$DOLPHIN_TAG" https://github.com/dolphin-emu/dolphin.git "$DEST"
fi
cd "$DEST"
ACTUAL="$(git rev-parse HEAD)"
if [ "$ACTUAL" != "$DOLPHIN_COMMIT" ]; then
  echo "ERROR: Dolphin checkout is $ACTUAL, expected $DOLPHIN_COMMIT ($DOLPHIN_TAG)" >&2
  exit 1
fi

if [ "${SKIP_SUBMODULES:-0}" = "1" ]; then
  echo "==> SKIP_SUBMODULES=1: not fetching submodules (patch/overlay check only)"
else
echo "==> Initialising submodules (Windows-only Qt and FFmpeg binaries are skipped)"
mapfile -t SUBS < <(git config -f .gitmodules --get-regexp '^submodule\..*\.path$' | awk '{print $2}' \
  | grep -v -E '^Externals/(Qt|FFmpeg-bin)$')
git submodule update --init --recursive --depth 1 --jobs 8 -- "${SUBS[@]}"
fi

echo "==> Applying patches"
for p in "$ROOT"/patches/*.patch; do
  if git apply --reverse --check "$p" 2>/dev/null; then
    echo "    already applied: $(basename "$p")"
  else
    git apply --whitespace=nowarn "$p"
    echo "    applied: $(basename "$p")"
  fi
done

echo "==> Copying launcher sources into Source/Android/app"
APP="$DEST/Source/Android/app/src"
mkdir -p "$APP/main/java/com" "$APP/test/java/com"
rm -rf "$APP/main/java/com/codblackops" "$APP/test/java/com/codblackops"
cp -r "$ROOT/app/src/main/java/com/codblackops" "$APP/main/java/com/"
cp -r "$ROOT/app/src/test/java/com/codblackops" "$APP/test/java/com/"
cp -r "$ROOT/app/src/main/res/." "$APP/main/res/"

echo "==> Dolphin prepared in $DEST"
