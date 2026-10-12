#!/bin/bash
# Prints the iOS build number: 1000 plus the number of commits on the current branch, so every main commit gets a
# higher one wherever it is built.
set -euo pipefail

cd "$(dirname "$0")"
if [[ "$(git rev-parse --is-shallow-repository)" == "true" ]]; then
  echo "error: shallow clone, fetch the full history to compute the build number" >&2
  exit 1
fi
echo $((1000 + $(git rev-list --count HEAD)))
