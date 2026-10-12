#!/usr/bin/env bash
# Sourced by the release scripts: helpers shared by the iOS and Android paths.

# Usage: require_unreleased_tag <tag> <release|--dry-run>
require_unreleased_tag() {
  local tag="$1" mode="$2"
  [[ -n "$(git ls-remote --tags origin "refs/tags/$tag")" ]] || return 0
  [[ "$mode" == "--dry-run" ]] || { echo "Refusing to release: $tag already exists."; exit 1; }
  echo "Note: $tag already exists, the dry run goes on."
}

# Usage: keychain_secret <service>   (prints the secret stored by `security add-generic-password -s <service>`)
keychain_secret() {
  security find-generic-password -s "$1" -w 2> /dev/null
}

export_github_token() {
  GH_TOKEN="${GH_TOKEN:-$(gh auth token --user Jju17 2> /dev/null || gh auth token)}"
  export GH_TOKEN
}
