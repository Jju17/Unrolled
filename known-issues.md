# Known issues

Living tracker for the whole repo: what is open, what was resolved and when. Historical audits live in git history, not here.

## Open

- **[M] No UI or snapshot tests** on either client (swift-snapshot-testing / Paparazzi). Reducer and ViewModel suites are the only regression net.
- **[M] No instrumented tests** for `MediaStorePhotoRepository` (cursor lifecycle, partial access, bucket filtering, `loadPhotosByIds` chunking).
- **[L] Android `versionCode = 2`** must be confirmed against the Play Console release history before the first upload.
- **[L] Dynamic Type.** Both clients use fixed point sizes; the 11 pt "ALBUM" label is unreadable at accessibility sizes.
- **[L] Image load failure** leaves a spinner on the card (`PhotoAssetImage`, Coil `AsyncImage` without error state). Needs a placeholder after a timeout.
- **[L] Locales.** In-app strings exist in en + fr only; store copy already has nl-NL.
- **[M] Similar-photo thresholds** (10 s gap, Hamming distance 12 on a 9x8 dHash) were chosen from the literature, not tuned on real libraries. Expect false negatives on HDR/exposure brackets and false positives on uniform scenes (sky, walls). Tune with a labelled sample before promoting the badge further.
- **[M] Android 17 (API 37)** is the compile/target level required by the current AndroidX releases but has not been exercised on a device. Smoke-test videos, the trash request and notifications on an API 37 emulator before the first upload.
- **[L] Video playback** is the system player on both platforms (`AVKit` / `VideoView`): no scrubbing polish, no mute toggle, audio follows the ringer. Good enough to decide keep or delete.
- **[L] Daily reminder** on Android uses `AlarmManager.setInexactRepeating`, so the notification lands within the system's batching window, not at the exact minute.
- **[L] iOS byte counts** come from `PHAssetResource` and are fetched lazily; the session summary can briefly show a lower "to free" figure until sizes arrive.

## Resolved

- 2026-10-12: CI. `verify.yml` runs the four gates on push and pull request, each job only when its paths changed, caches written from `main` only; Dependabot grouped weekly; releases and deploys from the Mac (`scripts/release-ios.sh`, `scripts/release-android.sh`, `scripts/deploy.sh`) after waiting for the Verify run. Functions moved to Node 24. First TestFlight archive still to be run once to confirm the team id in `project.yml`.

- 2026-10-12: product pass. Piles (month, on this day, a year ago, screenshots, videos, albums) with progress, session summary with bytes to free and confetti, lifetime storage-freed counter and stats sheet, similar-photo grouping (on-device dHash), videos in the deck with full-screen playback, daily goal ring, streak with weekly freeze, milestones with shareable cards, optional local reminder, "Goals and streaks" switch. Web landing page written.

- 2026-10-12: review pass. Trash sheet resolved from persisted IDs on both platforms; Android no longer reloads the deck on every resume (silent refresh keeps the top card); kept/deleted lists cannot hold duplicates and "Start over" forgets the album's decisions; iOS fetches cancel in flight; Android fullscreen pager no longer swallowed by the dismiss gesture; album picker scrollable with back handling; targetSdk 36; user-cancelled trash dialog is silent; Geocoder error path resumes; Live Photo requests deduplicated; fullscreen pager windowed on iOS; invisible action bar no longer tappable; stats pill always opens the sheet; accessibility labels and 44/48 hit areas; insets from the system; stamps localized on iOS; album names localized on Android; privacy copy made truthful about geocoding; dead code removed; dependencies bumped to the highest stable versions; functions test suite no longer empty.
