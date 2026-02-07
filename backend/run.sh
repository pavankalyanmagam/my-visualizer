#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT_DIR/out"

mkdir -p "$OUT_DIR"

# Kill existing process on port 8080 if any
if lsof -ti:8080 >/dev/null; then
  echo "Stopping existing server on port 8080..."
  lsof -ti:8080 | xargs kill -9
fi

javac -g --add-modules jdk.jdi -d "$OUT_DIR" "$ROOT_DIR"/src/visualizer/*.java
java --add-modules jdk.jdi -cp "$OUT_DIR" visualizer.Server
