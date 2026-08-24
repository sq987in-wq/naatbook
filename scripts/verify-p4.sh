#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# debug is the CI-published, debug-signed release-like R8 build. Build release
# as well so the production optimization path is validated even without a
# private upload keystore (AGP emits an unsigned release APK in that case).
./gradlew testDebugUnitTest assembleDebug assembleRelease --stacktrace

git diff --check

if git diff --name-only origin/main...HEAD -- .github/workflows | grep -q .; then
  echo "ERROR: verification detected an unauthorized workflow change" >&2
  exit 1
fi

shopt -s nullglob
for variant in debug release; do
  apks=("app/build/outputs/apk/${variant}"/*.apk)
  if ((${#apks[@]} == 0)); then
    echo "ERROR: expected an APK for the ${variant} variant" >&2
    exit 1
  fi
  for apk in "${apks[@]}"; do
    if [[ ! -s "$apk" ]]; then
      echo "ERROR: generated APK is empty: $apk" >&2
      exit 1
    fi
  done
done

echo "P4 verification passed: focused tests, optimized debug/release APKs, whitespace, and workflow guard."
