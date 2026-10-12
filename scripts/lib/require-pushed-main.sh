#!/usr/bin/env bash
# Sourced by the release paths: refuses anything but a clean main that matches origin/main.
# Usage: require_pushed_main <action, for example "deploy">

require_pushed_main() {
  local action="$1"
  [[ -z "$(git status --porcelain)" ]] || { echo "Refusing to $action: the working tree has uncommitted changes."; exit 1; }
  [[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || { echo "Refusing to $action: not on main."; exit 1; }
  git fetch --quiet origin main
  [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "Refusing to $action: HEAD is not origin/main."; exit 1; }
}
