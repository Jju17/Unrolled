#!/bin/bash
# shellcheck disable=SC2034
# Sourced by the archive and upload scripts: sets the xcodebuild auth flags. With an App Store Connect key in the
# environment xcodebuild signs and uploads with it; on a Mac without one it uses the Apple ID signed in to Xcode.
authentication=(-allowProvisioningUpdates)

if [[ -n "${CI:-}" ]]; then
  : "${APP_STORE_CONNECT_API_KEY_P8:?APP_STORE_CONNECT_API_KEY_P8 is not set}"
fi

if [[ -n "${APP_STORE_CONNECT_API_KEY_P8:-}" ]]; then
  : "${APP_STORE_CONNECT_API_KEY_ID:?APP_STORE_CONNECT_API_KEY_ID is not set}"
  : "${APP_STORE_CONNECT_API_ISSUER_ID:?APP_STORE_CONNECT_API_ISSUER_ID is not set}"
  key_dir="$(mktemp -d)"
  trap 'rm -rf "$key_dir"' EXIT
  key_path="$key_dir/AuthKey_${APP_STORE_CONNECT_API_KEY_ID}.p8"
  printf '%s\n' "$APP_STORE_CONNECT_API_KEY_P8" > "$key_path"
  authentication+=(
    -authenticationKeyPath "$key_path"
    -authenticationKeyID "$APP_STORE_CONNECT_API_KEY_ID"
    -authenticationKeyIssuerID "$APP_STORE_CONNECT_API_ISSUER_ID"
  )
fi
