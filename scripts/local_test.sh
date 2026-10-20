#!/usr/bin/env bash
# Compiles and runs the pure-Java core of the launcher and its unit tests on a plain JDK,
# without Android SDK or Maven. CI runs the same tests with real JUnit through Gradle.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/codbo-local-test"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
mapfile -t SRC < <(find "$ROOT/app/src/main/java/com/codblackops/mobile/core" -name '*.java')
mapfile -t TESTS < <(find "$ROOT/app/src/test/java" -name '*Test.java')
mapfile -t SHIM < <(find "$ROOT/scripts/junit-shim" -name '*.java')
javac --release 17 -Xlint:all -Werror -d "$OUT/classes" "${SHIM[@]}" "${SRC[@]}" "${TESTS[@]}" "$ROOT/scripts/LocalTestRunner.java"
CLASSES=()
for t in "${TESTS[@]}"; do
  rel="${t#$ROOT/app/src/test/java/}"
  CLASSES+=("$(echo "${rel%.java}" | tr '/' '.')")
done
java -cp "$OUT/classes" LocalTestRunner "${CLASSES[@]}"
