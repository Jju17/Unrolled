#!/usr/bin/env bash
# The only path to Google Play: checks the commit, replays the Android gate, builds the signed bundle on this Mac, then tags.
# Usage: scripts/release-android.sh <version> [--dry-run]   (--dry-run builds the bundle without tagging)
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:?usage: scripts/release-android.sh <version> [--dry-run]}"
mode="${2:-release}"
[[ "$mode" == "release" || "$mode" == "--dry-run" ]] || { echo "usage: scripts/release-android.sh <version> [--dry-run]"; exit 64; }
tag="android-v$version"
bundle="android/app/build/outputs/bundle/release/app-release.aab"
work_dir="$(mktemp -d)"

# shellcheck source-path=SCRIPTDIR/..
source scripts/lib/require-pushed-main.sh
# shellcheck source-path=SCRIPTDIR/..
source scripts/lib/release.sh
require_pushed_main release
scripts/check-release-version.sh "$version"
require_unreleased_tag "$tag" "$mode"

[[ -f android/keystore.jks ]] || { echo "Refusing to release: no upload keystore at android/keystore.jks."; exit 1; }
for property in RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
  grep -q "^$property=" "$HOME/.gradle/gradle.properties" 2> /dev/null \
    || { echo "Refusing to release: $property is missing from ~/.gradle/gradle.properties."; exit 1; }
done
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[[ -d "$ANDROID_HOME" ]] || { echo "Refusing to release: no Android SDK at $ANDROID_HOME."; exit 1; }

export_github_token
sha="$(git rev-parse HEAD)"
scripts/ci/wait-for-verify.sh "$sha" > "$work_dir/verify.log" 2>&1 &
verify_pid=$!
trap 'kill "$verify_pid" 2> /dev/null || true' EXIT

echo "== Gate: Android"
(cd android && ./gradlew -q :app:testDebugUnitTest :app:lintDebug --warning-mode=fail) > "$work_dir/gate.log" 2>&1 \
  || { tail -40 "$work_dir/gate.log"; echo "FAILED: Android gate, full log in $work_dir/gate.log"; exit 1; }

echo "== Building $version"
(cd android && ./gradlew -q :app:bundleRelease) > "$work_dir/bundle.log" 2>&1 \
  || { tail -40 "$work_dir/bundle.log"; echo "FAILED: bundle, full log in $work_dir/bundle.log"; exit 1; }
"$JAVA_HOME/bin/jarsigner" -verify "$bundle" | grep -q "jar verified" || { echo "FAILED: the bundle is not signed"; exit 1; }
"$JAVA_HOME/bin/jarsigner" -verify -verbose -certs "$bundle" | grep -q "CN=Android Debug" \
  && { echo "FAILED: the bundle is signed with the debug key, check ~/.gradle/gradle.properties"; exit 1; }

echo "== Waiting for Verify on $sha"
wait "$verify_pid" || { cat "$work_dir/verify.log"; echo "FAILED: Verify did not pass on $sha"; exit 1; }

if [[ "$mode" == "--dry-run" ]]; then
  echo "Dry run: signed bundle at $bundle, no tag pushed."
  exit 0
fi

git tag "$tag" "$sha"
git push --quiet origin "$tag"
echo "== $version signed at $bundle, tagged $tag. Upload it in the Play Console."
