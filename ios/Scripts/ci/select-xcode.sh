#!/bin/bash
# Switches the CI runner to the newest released Xcode: betas and release candidates are skipped.
set -euo pipefail

newest_xcode="$(find /Applications -maxdepth 1 -name 'Xcode_*.app' | grep -viE 'beta|release_candidate' | sort -V | tail -1)"
if [ -z "$newest_xcode" ]; then
  echo "error: no Xcode found in /Applications" >&2
  exit 1
fi
sudo xcode-select --switch "$newest_xcode"
xcodebuild -version
echo "version=$(xcodebuild -version | sed -n 's/^Xcode //p')" >> "${GITHUB_OUTPUT:-/dev/null}"
