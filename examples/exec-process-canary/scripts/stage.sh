#!/bin/sh
# Stage an intermediate artifact; never compiled or packaged, only consumed downstream.
set -eu
seed=$(cat "$ZOLT_PROJECT_ROOT/src/main/exec/seed.txt")
mkdir -p "$ZOLT_OUTPUT_DIR"
printf '%s\n' "$seed" > "$ZOLT_OUTPUT_DIR/staged.txt"
