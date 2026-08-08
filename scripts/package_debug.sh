#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/verify_no_internet_permission.py
python3 scripts/verify_models.py
if [ -x ./gradlew ]; then
  GRADLE=./gradlew
elif command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
else
  echo "ERROR: Gradle/Gradle Wrapper not found. Open the project in Android Studio or install Gradle first." >&2
  exit 3
fi
"$GRADLE" :app:assembleDebug
SRC="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$SRC" ] || SRC="$(find "$ROOT/app/build/outputs/apk/debug" -name '*.apk' | head -1)"
cp "$SRC" "$ROOT/MQVOX-debug.apk"
echo "Created $ROOT/MQVOX-debug.apk"
