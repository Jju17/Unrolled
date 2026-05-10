# Code audit — Unrolled iOS & Android

Generated audit of the iOS (`ios/`) and Android (`android/`) clients. Each finding cites a file path + line number so you can jump straight to the source. Findings are grouped by platform, then ranked by impact.

**TL;DR** — Both apps are launch-ready in terms of architecture and privacy. The remaining gaps are localization holes, a few force-unwraps, missing accessibility labels, and the absence of UI / snapshot tests. Nothing blocking; everything below is polish before Store submission.

---

## iOS

### Bugs / correctness

- **Force-unwrapped URLs** — `ios/Unrolled/Features/AboutSheet.swift:16-17` builds `privacyPolicyURL` and `supportURL` with `URL(string: "...")!`. A typo in either constant ships a guaranteed crash on About-screen open. Replace with `URL(string:)` returning optional + a `@ViewBuilder` guard, or use a tested constant helper.
- **Image cache prefetch race (theoretical)** — `ios/Unrolled/Features/PhotoAssetImage.swift:103-117`. The `actor PhotoImageCache.cachedIDs` set is updated from `prefetch` but read from concurrent `loadImage` callers. `PHCachingImageManager` itself is thread-safe, so the worst case is a duplicate request, not a crash.
- **Tight off-by-one window in deletion remap** — `ios/Unrolled/Features/SwipeFeature.swift:245-250`. Index recompute after deleted-batch confirmation falls back to `photos.count` when no match is found. The `startSwipe` guard at line 383 catches it before any out-of-bounds access, but the fallback should probably be `photos.count - 1`.

### Architecture (TCA) — adherence

✅ All actions named after user intent (`.deleteButtonTapped`, `.cardDragEnded`, `.albumPicked`).
✅ `@Dependency(\.continuousClock)`, `@Dependency(\.openURL)`, `@Dependency(\.photoLibrary)` used everywhere; no `Date()` / `Task.sleep` / direct PhotoKit calls in features.
✅ `enum CancelID { case emptyTrash, fetchAlbums, fetchPhotos, swipeAnimation }` — every long-running effect is `.cancellable(id:)`.
✅ No legacy APIs (`reduce(into:)`, `ViewStore`, `WithViewStore`, `TaskResult`, `@PresentationState`).
✅ `@State` is used only for view-local concerns (image-load progress, fly-off overlay, zoom transform).
✅ `PhotoLibraryClient` properly conforms to `DependencyKey` with `liveValue` / `testValue` / `previewValue`.

### Performance

✅ `PHCachingImageManager` warmed via `prefetchUpcoming` on `activeIndex` change (`SwipeScreen.swift:338`), window of 5 cards.
✅ Image targets clamped to 4096 px in `PhotoAssetImage.swift:80-86` to avoid OOM on older devices.
✅ Card-stack transforms are pure `CGFloat` math; no expensive view recomputation per drag.
- **DeletedPhotosSheet builds a dictionary on every render** — `ios/Unrolled/Features/DeletedPhotosSheet.swift:36-39` materializes `[ID: PhotoAsset]` from `store.photos` every time the sheet redraws. Fine for typical libraries; consider memoizing in state if libraries grow past a few thousand photos.

### Privacy

✅ `Info.plist` declares `NSPhotoLibraryUsageDescription`; `PrivacyInfo.xcprivacy` correctly declares `UserDefaults` (CA92.1) and `SystemBootTime` (35F9.1) with no tracking, no data collection, no tracking domains.
✅ No `URLSession` / no analytics / no third-party SDKs. About-sheet links route to Safari via `Link` / `.openURL`.
✅ PhotoKit authorization requested as `.readWrite`; only used for library reads + `PHAssetChangeRequest.deleteAssets`.

### Accessibility

- **Missing `accessibilityLabel` on icon buttons** — `ios/Unrolled/Features/SwipeScreen.swift`:
  - `:112-129` settings gear → add "Settings".
  - `:140-156` album-title button → add "Select album".
  - `:162-167` shuffle toggle → add "Shuffle photos".
- **Hardcoded `.system(size: …)` defeats Dynamic Type** — Throughout `SwipeScreen.swift`. The `"ALBUM"` label at size 11 is unreadable at the highest accessibility sizes. Move to semantic fonts (`.headline`, `.caption`) + `.tracking(...)`.
- ✅ Action buttons (Delete / Undo / Keep) at `SwipeScreen.swift:490, 501, 509` have full a11y labels and 58 pt hit targets.

### i18n

- **~9 user-facing strings hardcoded English** despite the French locale being declared in `Localizable.xcstrings`:
  - `SwipeScreen.swift`: `"ALBUM"` (:136), `"No photos found"` (:424), `"Your library is empty."` (:427), `"Album finished"` (:442), `"Change album"` (:457), `"Start over"` (:462).
  - `DeletedPhotosSheet.swift`: `"No photos to delete"` (:67), `"Swipe left to fill the trash."` (:70), `"Ready to delete"` (:120), `"Keep"` (:131).
- **Stale `extractionState` markers** in `Localizable.xcstrings` — entries with the old `%lld` format (lines ~61–94) are out of sync with current `%@` callsites. Rerun Xcode's localization extraction.
- ✅ `String(localized: "Delete \(count) photos?")` is used correctly with plural variants (`SwipeScreen.swift:91`).

### Testing

- ✅ `UnrolledTests/SwipeFeatureTests.swift` — 777 lines, ~1.8× the production reducer. Covers auth flow, album switching, drag mechanics, undo edge cases (negative-clamp guard), trash empty (success + error), shuffle position restoration, cancellation, computed properties, presentation flags.
- ⚠️ Zero view-layer / snapshot coverage. `PhotoAssetImage`, `ZoomableScrollView`, the gesture surface in `SwipeScreen` could regress visually with no failing test.
- ⚠️ `PhotoImageCache` actor and image-loading paths untested.

---

## Android

### Bugs / correctness

- **Hardcoded French in iOS-localized app** — `android/app/src/main/java/dev/rahier/unrolled/FullscreenPhotoView.kt:82` sets `contentDescription = "Fermer"`. Non-French TalkBack users hear French. Add `R.string.fullscreen_close_a11y` and translate it.
- **Hardcoded English stamps** — `CardStack.kt:290-291` renders `Stamp("KEEP", …)` / `Stamp("DELETE", …)` as on-card swipe feedback. Should be `stringResource(R.string.stamp_keep)` / `stamp_delete` with French equivalents.
- **`state.topPhoto!!` force-unwrap** — `SwipeScreen.kt:291-295`. Guarded by a null check immediately above, so safe in practice, but the `!!` should be replaced with the safe-cast pattern (`val top = state.topPhoto ?: return`) to remove the crash vector entirely.

### Architecture (MVI) — adherence

✅ Immutable `SwipeState` with `val`-only fields; no `remember { mutableStateOf }` for business state.
✅ Intent names follow user-action convention (`DeleteButtonTapped`, `AlbumPicked`); effect-completion intents (`SwipeExitAnimationCompleted`, `TrashResult`) included.
✅ Density-dependent values arrive on intents (`CardDragEnded(thresholdPx)`); VM never touches `LocalDensity`.
✅ ViewModel never sees `Activity` / `Context`; `PhotoRepository` injected via `Factory` from `MainActivity`.
✅ `viewModelScope` jobs (`animationJob`, `photosJob`, `albumsJob`) cancelled on state-invalidating intents.
- **Minor field-ordering nit** — `SwipeState.kt:42` places `hasPartialPhotoAccess` before `photos` (line 45). Strict alphabetical order would re-position it; not functional, just style.

### Performance

✅ Coil 3 core-only (no `coil-network-okhttp` — explicitly excluded in `app/build.gradle.kts:89-93`); `content://` URIs resolve via the built-in ContentResolver fetcher.
✅ `key(photo.id)` on entrance + exit overlays in `CardStack.kt:67, 82` keeps recomposition stable.
✅ Drag uses `snap()` while dragging, `spring()` on release (`CardStack.kt:121-129`) — smooth at 60 fps.
✅ `LazyColumn` in `DeletedPhotosSheet` keys items by id.
- `PhotoRepository.loadAlbums()` dedupes albums in-memory via `linkedMapOf` (`:86-101`). Acceptable; MediaStore `BUCKET_ID` queries can't `DISTINCT` directly.

### Privacy

✅ `AndroidManifest.xml` declares `READ_MEDIA_IMAGES` (API 33+) + `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+) + `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"`.
✅ `backup_rules.xml` and `data_extraction_rules.xml` exclude all domains — the app holds no private state beyond the user's MediaStore.
✅ Partial photo access (API 34+) detected and surfaced in the UI (`PhotoRepository.hasPartialPhotoAccess()` + permission-rationale-aware empty state).
✅ `shouldShowRequestPermissionRationale()` is consulted before falling back to the system Settings deep-link.
✅ No networking — Coil core only, no HTTP client, no analytics.

### Accessibility

- Hardcoded swipe stamps + hardcoded French close-button label (see *Bugs* above) leak through to TalkBack.
- 13 `contentDescription = null` sites — most decorative, but verify TalkBack flow on the swipe gesture surface and re-add labels where the surrounding text alone is ambiguous.
- ✅ Action buttons (`action_delete_a11y`, `action_keep_a11y`, `action_undo_a11y`) and DeletedPhotosSheet close button have proper labels.
- ✅ Header album picker uses `role(Role.Button)`.

### i18n

- ✅ `strings.xml` (en) and `values-fr/strings.xml` (fr) are perfectly in sync — 82 keys each. Plurals correctly handled (`done_subtitle_kept`, `done_subtitle_deleted`, `deleted_alert_title`).
- ❌ Hardcoded outliers: `CardStack.kt:290-291` (KEEP/DELETE stamps), `FullscreenPhotoView.kt:82` (Fermer).
- Missing string resources to add: `stamp_keep`, `stamp_delete`, `fullscreen_close_a11y`.

### Build & release

- ✅ R8 enabled (`isMinifyEnabled = true`, `isShrinkResources = true`); minimal `proguard-rules.pro` overrides on top of library consumer rules.
- ✅ Signing fallback: release config falls back to the debug key when `RELEASE_*` Gradle properties are absent, so `assembleRelease` always succeeds locally; Play App Signing then re-signs server-side.
- ⚠️ **`versionCode = 2` with `versionName = "1.0.0"`** — `app/build.gradle.kts:15-16`. Probably bumped during testing; clarify before the first Play upload (Play won't let you release a lower code later).
- ✅ **Namespace + applicationId aligned** — both are `dev.rahier.unrolled` after the migration from the legacy `picswipe` namespace.

### Testing

- ✅ `SwipeViewModelTest.kt` — 50+ tests across permission flow, load errors, album picker, drag/swipe, deleted-review sheet, undo edge cases, reset, shuffle, cancellation, presentation flags, computed props, end-to-end Turbine flow.
- ✅ Uses `StandardTestDispatcher` + `advanceTimeBy` for timing assertions; `Turbine` for state-flow validation.
- ⚠️ No Compose snapshot tests — Paparazzi or a Showkase setup would catch UI regressions on `SwipeScreenContent`, `EmptyStates`, `DeletedPhotosSheet` rows.
- ⚠️ `MediaStorePhotoRepository` has no instrumented tests — its cursor logic is exercised only on real devices.
- ⚠️ Permission-launcher flow in `SwipeScreen.kt:59-74` is Activity-scoped and not covered.

---

## Cross-cutting recommendations

Order by impact across both platforms:

1. **Localize the stamps and close labels (Android)** — short keys, two locales, ~30 minutes total. Highest visible-bug-per-effort ratio.
2. **Replace force-unwraps (iOS About URLs, Android `topPhoto!!`)** — eliminates two crash vectors; both fixes are 1-line changes.
3. **Add the 9 missing iOS string entries + rerun extraction** — gets iOS to feature parity with Android's complete fr-FR coverage.
4. **Add the 3 missing iOS accessibility labels** (settings / album / shuffle).
5. **Confirm Android `versionCode`** before the first Play upload.
6. **Snapshot tests on both platforms** — Paparazzi (Android), `swift-snapshot-testing` (iOS). Single biggest test-coverage gain.
7. **Both sets of architecture rules are obeyed** — TCA on iOS, MVI on Android. Don't loosen them; they're the reason the audit is this short.
8. **Update `.claude/rules/android.md`** to reflect the current `applicationId` (`dev.rahier.unrolled`) and to remove the "deletion not wired yet" note (deletion is wired on both platforms via `MediaStore.createTrashRequest` / `PHAssetChangeRequest.deleteAssets`).

## Risk summary

| Area | iOS | Android |
| --- | --- | --- |
| Architecture | ✅ | ✅ |
| Privacy | ✅ | ✅ |
| Crashes / correctness | ⚠️ 2 force-unwraps | ⚠️ 1 force-unwrap |
| Accessibility | ⚠️ 3 missing labels + dynamic type | ⚠️ 2 hardcoded labels |
| i18n | ⚠️ 9 missing strings | ⚠️ 2 hardcoded strings |
| Testing | ✅ logic / ⚠️ UI | ✅ logic / ⚠️ UI |
| Release config | n/a | ⚠️ `versionCode` |

Overall: **A−**. Both clients are in good shape. The list above is a clean polish backlog before public Store launches, not a refactor.
