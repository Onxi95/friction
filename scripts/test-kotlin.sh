#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/tmp/kotlin-test"
JAR_PATH="$OUT_DIR/focusgate-kotlin-tests.jar"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

mapfile -t SOURCES < <(find "$ROOT/android/src/main/kotlin" "$ROOT/android/src/test/kotlin" -name '*.kt' | sort)

kotlinc "${SOURCES[@]}" -include-runtime -d "$JAR_PATH"
java -jar "$JAR_PATH"
