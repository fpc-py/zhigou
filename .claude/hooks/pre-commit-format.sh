#!/bin/bash
CMD="$1"
if echo "$CMD" | grep -qE 'rm\s+-rf\s+/'; then
  echo "[BLOCKED] $CMD"; exit 1
fi
exit 0