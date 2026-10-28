#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_NAME="NoChatFreeze.jar"

"$ROOT_DIR/gradlew" --no-daemon clean build
cp "$ROOT_DIR/build/libs/$JAR_NAME" "$ROOT_DIR/$JAR_NAME"

echo "Built $ROOT_DIR/$JAR_NAME"
