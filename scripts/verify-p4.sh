#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

./gradlew testDebugUnitTest assembleDebug --stacktrace
git diff --check

if git diff --name-only origin/main...HEAD -- .github/workflows | grep -q .; then
  echo "ERROR: verification detected an unauthorized workflow change" >&2
  exit 1
fi

echo "P4 verification passed: unit tests, debug APK, whitespace, and workflow guard."
