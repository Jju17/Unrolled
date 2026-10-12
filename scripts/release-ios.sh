#!/usr/bin/env bash
# The only path to TestFlight: checks the commit, replays the iOS gate, archives and uploads from this Mac, then tags.
# Usage: scripts/release-ios.sh <version> [--dry-run]   (--dry-run stops after the archive: no upload, no tag)
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:?usage: scripts/release-ios.sh <version> [--dry-run]}"
mode="${2:-release}"
[[ "$mode" == "release" || "$mode" == "--dry-run" ]] || { echo "usage: scripts/release-ios.sh <version> [--dry-run]"; exit 64; }
tag="ios-v$version"
keychain_service="rahier-dev-app-store-connect"
work_dir="$(mktemp -d)"

# shellcheck source-path=SCRIPTDIR/..
source scripts/lib/require-pushed-main.sh
# shellcheck source-path=SCRIPTDIR/..
source scripts/lib/release.sh
require_pushed_main release
scripts/check-release-version.sh "$version"
require_unreleased_tag "$tag" "$mode"
[[ "$(xcode-select -p)" != *[Bb]eta* ]] || { echo "Refusing to release: App Store Connect rejects builds from a beta Xcode."; exit 1; }
command -v xcodegen > /dev/null || { echo "Refusing to release: brew install xcodegen first."; exit 1; }

export_github_token
sha="$(git rev-parse HEAD)"
scripts/ci/wait-for-verify.sh "$sha" > "$work_dir/verify.log" 2>&1 &
verify_pid=$!
trap 'kill "$verify_pid" 2> /dev/null || true' EXIT

if security find-generic-password -s "$keychain_service" > /dev/null 2>&1; then
  key_attributes="$(security find-generic-password -s "$keychain_service")"
  APP_STORE_CONNECT_API_KEY_ID="$(sed -n 's/^ *"acct"<blob>="\(.*\)"$/\1/p' <<<"$key_attributes")"
  APP_STORE_CONNECT_API_ISSUER_ID="$(sed -n 's/^ *"icmt"<blob>="\(.*\)"$/\1/p' <<<"$key_attributes")"
  APP_STORE_CONNECT_API_KEY_P8="$(security find-generic-password -s "$keychain_service" -w | base64 --decode)"
  export APP_STORE_CONNECT_API_KEY_ID APP_STORE_CONNECT_API_ISSUER_ID APP_STORE_CONNECT_API_KEY_P8
  echo "== Signing with the App Store Connect key $APP_STORE_CONNECT_API_KEY_ID"
else
  echo "== No App Store Connect key in the keychain ($keychain_service): signing with the Apple ID signed in to Xcode"
fi

echo "== Gate: iOS"
(cd ios && xcodegen generate --quiet && xcodebuild test \
  -project Unrolled.xcodeproj \
  -scheme Unrolled \
  -destination 'platform=iOS Simulator,OS=latest,name=iPhone 17' \
  -derivedDataPath ../DerivedData \
  -skipPackagePluginValidation -skipMacroValidation \
  COMPILER_INDEX_STORE_ENABLE=NO \
  -quiet) || { echo "FAILED: iOS tests"; exit 1; }

build_number="$(ios/Scripts/ci/build-number.sh)"
archive_path="$work_dir/Unrolled.xcarchive"
echo "== Archiving $version ($build_number)"
ios/Scripts/ci/archive.sh "$build_number" "$archive_path" > "$work_dir/archive.log" 2>&1 \
  || { tail -40 "$work_dir/archive.log"; echo "FAILED: archive, full log in $work_dir/archive.log"; exit 1; }

echo "== Waiting for Verify on $sha"
wait "$verify_pid" || { cat "$work_dir/verify.log"; echo "FAILED: Verify did not pass on $sha"; exit 1; }

if [[ "$mode" == "--dry-run" ]]; then
  echo "Dry run: archive ready at $archive_path, nothing uploaded, no tag pushed."
  exit 0
fi

echo "== Uploading to TestFlight"
ios/Scripts/ci/upload-testflight.sh "$archive_path" || { echo "FAILED: TestFlight upload"; exit 1; }
git tag "$tag" "$sha"
git push --quiet origin "$tag"
echo "== $version ($build_number) is processing on TestFlight, tagged $tag"
