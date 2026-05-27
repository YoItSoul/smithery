#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

git config --global user.name "YoItSoul"
git config --global user.email "scalfua@gmail.com"
