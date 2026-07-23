#!/bin/sh
# Consume the staged intermediate (passed as $1) and emit a packaged resource.
set -eu
stage_dir="$1"
message=$(cat "$ZOLT_PROJECT_ROOT/$stage_dir/staged.txt")
mkdir -p "$ZOLT_OUTPUT_DIR"
printf 'canary.message=%s\ncanary.source=exec-process\n' "$message" > "$ZOLT_OUTPUT_DIR/exec-canary.properties"
