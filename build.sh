#!/usr/bin/env bash
# Build the release APK (platform-signed) and package the Magisk/KernelSU
# module zip into dist/. Works in Git Bash on Windows (7-Zip) and on Linux CI
# (Info-ZIP).
set -euo pipefail
cd "$(dirname "$0")"

VERSION="14.0-bg1"
DIST="dist"
MODULE="magisk-module"
APK_SRC="app/build/outputs/apk/release/app-release.apk"
OUT="$DIST/GameSpace-BurnInGuard-$VERSION.zip"

echo "==> Building release APK"
sh gradlew :app:assembleRelease --console=plain

if [ ! -f "$APK_SRC" ]; then
    echo "ERROR: $APK_SRC not produced" >&2
    exit 1
fi

echo "==> Staging module"
mkdir -p "$MODULE/system_ext/priv-app/GameSpace" "$DIST"
cp "$APK_SRC" "$MODULE/system_ext/priv-app/GameSpace/GameSpace.apk"

echo "==> Packaging $OUT"
rm -f "$OUT"
if [ -n "${SEVENZIP:-}" ]; then
    ZIP="$SEVENZIP"
elif command -v 7z >/dev/null 2>&1; then
    ZIP="7z"
elif [ -x "/c/Program Files/7-Zip/7z.exe" ]; then
    ZIP="/c/Program Files/7-Zip/7z.exe"
elif command -v zip >/dev/null 2>&1; then
    ZIP="zip"
else
    echo "ERROR: no 7z or zip binary found" >&2
    exit 1
fi

(
    cd "$MODULE"
    case "$ZIP" in
        *7z*) "$ZIP" a -tzip "../$OUT" . >/dev/null ;;
        *)    "$ZIP" -qry "../$OUT" . ;;
    esac
)

echo "==> Done: $OUT"
