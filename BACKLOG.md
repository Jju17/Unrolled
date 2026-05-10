# Backlog

Post-launch polish and follow-ups that came out of the [`audit.md`](audit.md) but were intentionally **not** done before the first Store submission.

The launch-blocking items (force-unwraps, hardcoded UI strings, missing accessibility labels, force-unwrapped `topPhoto`) were fixed before this backlog was filed — see commit history. Everything below is "should do, but not before shipping v1.0."

Conventions:
- **[H/M/L]** = high / medium / low priority.
- File references use the `path:line` format where applicable.

---

## iOS

### Code quality

- **[L]** Replace hardcoded `.system(size: …)` font sizes with semantic fonts (`.headline`, `.caption`, `.title2`) so Dynamic Type scales the UI. Pay attention to the `"ALBUM"` label at size 11 (`Features/SwipeScreen.swift:137`) — illegible at the highest accessibility sizes. Large refactor, deserves a design pass.
- **[L]** Memoize `DeletedPhotosSheet`'s `[id: PhotoAsset]` dictionary (`Features/DeletedPhotosSheet.swift:36-39`). Builds on every render — fine today, can stall on libraries past a few thousand photos. Move to a computed property in the reducer.
- **[L]** Add error handling to `PhotoAssetImage.loadImage` (`Features/PhotoAssetImage.swift:35-41`). Today an image-load failure leaves the spinner forever; should fall back to a placeholder after a timeout.
- **[L]** Telemetry / log when `undoButtonTapped` clamps a counter to 0 (`Features/SwipeFeature.swift:200, 326-328`). Defensive guard exists; firing it indicates state-machine corruption worth investigating in the wild.

### Testing

- **[M]** Snapshot tests for view layer (Paparazzi-equivalent — [`swift-snapshot-testing`](https://github.com/pointfreeco/swift-snapshot-testing)). Targets: `SwipeScreen`, `PhotoCard`, `FullscreenPhotoView`, `DeletedPhotosSheet`, `AlbumPickerSheet`, `AboutSheet`, `EmptyState` / `DoneView` / `PermissionGate`.
- **[L]** Unit-test `PhotoImageCache` actor (`Features/PhotoAssetImage.swift:103-117`) for prefetch idempotence and the theoretical race between `prefetch` and concurrent `loadImage` calls.
- **[L]** Unit-test `ZoomableScrollView` UIScrollView delegate logic — zoom centering, pan gating.

### Documentation / catalog hygiene

- **[L]** Run Xcode's localization extraction once (`xcodegen generate && xcodebuild build`) to refresh the `extractionState: stale` markers in `Resources/Localizable.xcstrings`. The strings are still used; only the markers are stale. Cosmetic.

---

## Android

### Code quality

- **[H]** Confirm `versionCode = 2` (`app/build.gradle.kts:15`) is intentional before the first Play upload. Likely bumped during internal testing — Play won't accept a lower code later, so verify this matches the planned release track history.
- **[L]** Audit the 13 `contentDescription = null` sites (mostly decorative icons) and verify the TalkBack flow on the swipe gesture surface — re-add labels where surrounding text alone is ambiguous.

### Testing

- **[M]** Compose snapshot tests via [Paparazzi](https://github.com/cashapp/paparazzi) or [Showkase](https://github.com/airbnb/Showkase). Targets: `SwipeScreenContent`, `PermissionGate`, `EmptyState` / `LoadErrorState` / `DoneView`, `DeletedPhotosSheet` rows, `AlbumPickerSheet`.
- **[M]** Instrumented tests for `MediaStorePhotoRepository` — cursor lifecycle, empty/malformed query results, partial-access detection (API 34+), bucket-id filtering.
- **[L]** Cover the permission launcher flow (`SwipeScreen.kt:59-74`) end-to-end via a Robolectric or instrumented harness — the `rememberLauncherForActivityResult` path is currently un-asserted.

### Build / release

- **[L]** Tune Coil 3 image-loader cache (`MainActivity.kt`) only if perf regresses on low-end devices. Defaults are reasonable today.
- **[L]** Add explicit `# Coil 3` block to `proguard-rules.pro` if the Play Console pre-launch report reports any reflection-related crash. Coil ships consumer rules; this is contingency.

---

## Cross-platform / infra

- **[M]** **CI pipeline.** Wire GitHub Actions (or equivalent) for: iOS — `xcodegen generate` + `xcodebuild test`; Android — `./gradlew testDebugUnitTest` + `./gradlew lintDebug`; Web — `npm run build`. Currently zero automated regression net at the PR level.
- **[M]** Bring snapshot testing online on both clients **before** the v1.1 cycle starts — UI regressions today are caught only by hand-testing.
- **[L]** Decide on (and ship?) lightweight, **opt-in**, privacy-respecting telemetry. Today the apps are deliberately offline; if any decision-quality data is ever needed (e.g. "what % of users finish their first album"), the contract has to be: opt-in with explicit consent, no PII, no third-party SDK, batched and dropped on revoke. Until that's designed, the apps stay offline.
- **[L]** Crash reporting. Same caveats as telemetry. If we add it, it must be opt-in and respect the `PrivacyInfo.xcprivacy` / Data Safety story.
- **[L]** Localization expansion — the apps are en + fr today. The `RELEASE_NOTES.md` already accommodates `<nl-NL>` for store copy, but the in-app strings have no `values-nl/strings.xml` (Android) or `nl` entries (iOS) yet. Decide whether to ship Dutch + which other locales.
- **[L]** Document the Android signing-fallback contract (debug key when `RELEASE_*` Gradle props are missing) at the top of `app/build.gradle.kts` for new contributors. Currently only explained in PR / commit history.

---

## What was already fixed for the launch

Recorded here for context — these are not action items, just what's already shipped:

- iOS: 2 force-unwrapped URLs in `AboutSheet`, 3 missing `accessibilityLabel`s on icon buttons (Settings / Album / Shuffle), 2 new strings added to `Localizable.xcstrings` with French.
- Android: hardcoded `KEEP` / `DELETE` swipe stamps and `Fermer` close-button label moved to `strings.xml` with French translations; `state.topPhoto!!` force-unwrap replaced with the safe-guard pattern; `hasPartialPhotoAccess` field re-alphabetised in `SwipeState`.
- Docs: `.claude/rules/{ios,android}.md` updated to reflect that deletion is wired (was marked "phase 2") and that Android's `applicationId` is `dev.rahier.unrolled`.

See [`audit.md`](audit.md) for the full audit and [git log] for the diffs.
