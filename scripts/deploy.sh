#!/usr/bin/env bash
# The only path to Firebase: hosting, functions, Firestore rules and indexes, from a clean and verified main.
set -euo pipefail
cd "$(dirname "$0")/.."

site="https://unrolled-e4775.web.app"
work_dir="$(mktemp -d)"

# shellcheck source-path=SCRIPTDIR/..
source scripts/lib/require-pushed-main.sh
# shellcheck source-path=SCRIPTDIR/..
source scripts/lib/release.sh
require_pushed_main deploy

export_github_token
sha="$(git rev-parse HEAD)"
scripts/ci/wait-for-verify.sh "$sha" > "$work_dir/verify.log" 2>&1 &
verify_pid=$!
trap 'kill "$verify_pid" 2> /dev/null || true' EXIT

echo "== Gate: functions"
(cd functions && npm ci --silent --no-audit --no-fund && npm run verify && npm run build) || { echo "FAILED: functions gate"; exit 1; }
echo "== Gate: web"
(cd web && npm ci --silent --no-audit --no-fund && npm run verify) || { echo "FAILED: web gate"; exit 1; }

echo "== Waiting for Verify on $sha"
wait "$verify_pid" || { cat "$work_dir/verify.log"; echo "FAILED: Verify did not pass on $sha"; exit 1; }

firebase projects:list --json > /dev/null 2>&1 || { echo "FAILED: run firebase login --reauth"; exit 1; }
echo "== Deploying"
firebase deploy --only hosting,functions,firestore:rules,firestore:indexes --non-interactive --force
for url in "$site/" "$site/privacy" "$site/support"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$url")
  [[ "$code" == "200" ]] || { echo "FAILED: $url answered $code after deploy"; exit 1; }
done
echo "== $site is live"
