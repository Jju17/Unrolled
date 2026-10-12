# Android: Unrolled

Native Kotlin + **Jetpack Compose** app built on an **MVI** architecture. `minSdk = 26`, `compileSdk = 37`, `targetSdk = 37`. Application id and namespace both `dev.rahier.unrolled`.

## Stack

- Kotlin 2.4 through AGP 9's built-in Kotlin support (no explicit `org.jetbrains.kotlin.android` plugin)
- Jetpack Compose: BOM `2026.09.00`, Material3, `material-icons-extended`
- AGP 9.4, Gradle 9.7
- [Coil 3](https://coil-kt.github.io/coil/) for image loading plus `coil-video` for video posters, no `coil-network-okhttp`: Coil resolves `content://` MediaStore URIs directly
- JUnit 4 + Turbine for VM tests, `kotlinx-coroutines-test` for deterministic timing

No analytics, no crash reporters, no Firebase SDK. The only data that leaves the device is the GPS coordinate of a photo sent to the system `Geocoder` to resolve a city name, and only while "Show location" is enabled in Settings. The daily reminder is a local `AlarmManager` alarm posting a notification, off by default.

Dependency policy: never pin, always bump to the highest stable version that keeps the gate green. If a bump has to be reverted, record the blocking version and the reason in the Gotchas below.

## JAVA_HOME (only required for the terminal)

Android Studio finds its own JBR; `./gradlew` from a terminal does not. Export once:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

## Build & install

```bash
cd android
./gradlew :app:assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug                       # build + push to selected device
adb shell am start -n dev.rahier.unrolled/dev.rahier.unrolled.MainActivity
```

## Gate

Run before every commit and before announcing a fix, then read the output:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --warning-mode=all
```

Lint runs with `warningsAsErrors = true`, so a new lint warning fails the build.

## Project layout

```
app/src/main/java/dev/rahier/unrolled/
├── UnrolledApp.kt            Application: Coil image loader (video decoder), notification channel
├── MainActivity.kt           entry; constructs the repositories + ViewModelProvider.Factory
├── SwipeState.kt             immutable data class (alphabetised fields) + LoadPurpose
├── SwipeIntent.kt            sealed interface (data object / data class)
├── SwipeViewModel.kt         owns StateFlow<SwipeState>, reduces intents
├── SwipeScreen.kt            top-level @Composable (collects state, owns launchers, overlays)
├── SwipeUi.kt, CardStack.kt  main screen UI + card stack (video and similar badges)
├── ActionBar.kt, Header.kt, EmptyStates.kt, Sheets.kt (pile picker, about), SettingsUi.kt
├── SessionSummary.kt, Confetti.kt          finished-pile view
├── StatsSheet.kt, ShareCard.kt             activity stats, milestone share card
├── SimilarPhotosSheet.kt                   keep / delete a group of near-duplicates
├── DeletedPhotosSheet.kt     trash review sheet
├── FullscreenPhotoView.kt, ZoomableDismissable.kt, Pinchable.kt
├── LocationDescriber.kt      EXIF GPS + Geocoder, cached per photo id
├── Logger.kt                 injectable logger (unit tests pass a no-op)
├── PhotoAsset.kt             value types (PhotoAsset, Pile, PileSelector)
├── ActivityStats.kt          ActivityStats, Milestone, ActivityTracker (pure)
├── SimilarityGrouper.kt      candidate runs, dHash, Hamming grouping (pure)
├── PhotoRepository.kt        wraps MediaStore queries (piles, photos, sizes, hashes)
├── ReminderScheduler.kt      AlarmManager reminder, ReminderReceiver, BootReceiver
├── SettingsRepository.kt, SwipeProgressRepository.kt, StatsRepository.kt   SharedPreferences persistence

app/src/main/res/
├── values/strings.xml        en (source of truth)
├── values-fr/strings.xml     fr (must stay in sync, key-for-key)
├── values-night/themes.xml
├── drawable/ic_notification.xml
├── mipmap-anydpi/            adaptive icons (with monochrome layer)
├── mipmap-*/                 adaptive foreground only (minSdk 26 never uses legacy PNGs)
└── xml/{backup_rules,data_extraction_rules,file_paths}.xml

app/src/test/java/dev/rahier/unrolled/
├── FakePhotoRepository.kt
├── ActivityTrackerTest.kt, SimilarityGrouperTest.kt
└── SwipeViewModelTest.kt
```

## Architecture: MVI

Every screen is a triple: immutable `State` data class, sealed `Intent` hierarchy, `ViewModel` exposing `StateFlow<State>`. Composables are stateless: they take `(state, onIntent)` and nothing else.

**Hard rules**:

- Intents named after **user actions** (`DeleteButtonTapped`, `PilePicked(selector)`), never after reducer behavior.
- Effect-completion signals are intents too (`SwipeExitAnimationCompleted`, `PhotosLoaded`, `TrashPhotosLoaded`) so every state change is traceable through `onIntent`.
- All business state lives on the data class. `remember { mutableStateOf(...) }` inside a composable is allowed only for purely-presentational state the VM does not care about (gesture transforms, dropdown expanded flag).
- ViewModels never see `Activity` or `Context`; platform access goes through the injected repositories.
- Density-dependent values (drag thresholds in px) come in on intents; the VM has no `LocalDensity`.
- `viewModelScope` jobs are stored as `private var fooJob: Job? = null` so a state-invalidating intent can cancel them before emitting new state.
- Alphabetise state fields, intent cases, composable parameter lists.

The full architecture playbook lives in [`.claude/rules/android.md`](../.claude/rules/android.md).

## Deck semantics

The persisted progress (`keptPhotoIDs`, `deletedPhotoIDs`) is the source of truth across sessions; the deck is "what is left to decide" in the selected pile (`selectedPile: PileSelector`: all, album, month, on this day, a year ago, screenshots, videos).

- `loadedPhotos`: the pile minus the ids already classified when it was loaded. `photos`: the deck, same content or a shuffled copy. Photos classified during the session stay in `photos` so undo works; `history` entries hold `PhotoDecision` lists referencing photo ids, not indices, and undo restores the whole entry.
- `pilePhotoCount`: unfiltered size of the pile. `isEmptyPile` (nothing in the pile) and `done` (everything decided) are derived from it. `piles` carries every pile with its `remainingCount` for the picker.
- `trashPhotos`: assets resolved for `deletedPhotoIDs` (via `loadPhotosByIds`), what the review sheet renders. Ids whose asset disappeared are pruned.
- Every classification removes the id from both lists before appending it to one: an id lives in exactly one list, never twice.
- `LoadPurpose`: `Initial` resets the session, `Refresh` (every ON_RESUME after the first load) keeps the user on the same photo and only drops removed photos / appends newcomers, `StartOver` ("Start over" on the done view) forgets the markers of the pile's photos first.
- Shuffle toggling rebuilds the deck from the still-unclassified photos; disabling it resumes at the first unclassified photo after the saved position.
- Session: `sessionStartedAt` / `sessionFinishedAt` (from the injected `clock`), `sessionKeptIds` / `sessionDeletedIds` feed the finished-pile summary (reviewed, kept, deleted, bytes to free from `PhotoAsset.sizeBytes`, duration, speed).
- `ActivityTracker` updates `stats` on every decision (reviewed, today's count, streak, milestones) and on a successful trash request (deleted, bytes freed). `SimilarityGrouper` turns `similarityHashes` into `similarGroups`; `topSimilarGroup` drives the badge.

## Photo library

`PhotoRepository` (`PhotoRepository.kt`) is the only place that touches `MediaStore`. The VM holds it via constructor injection; `MainActivity` builds the `Factory`.

Permission flow (`SwipeScreen.kt`):
1. Every `ON_RESUME` sends `ScreenAppeared`; the VM checks `hasReadPermission()`.
2. If not granted, the gate screen triggers `rememberLauncherForActivityResult(RequestMultiplePermissions())`. The result is derived from `hasReadPermission()` again (not from the grant map, which also contains `ACCESS_MEDIA_LOCATION`).
3. `Granted` loads photos, piles and the trash in parallel. `Denied` re-asks on the next tap; `PermanentlyDenied` (rationale refused) switches the button to "Open settings".
4. On API 34+, partial access is detected via `READ_MEDIA_VISUAL_USER_SELECTED` and surfaced in the empty state with a "Change selection" affordance.

Image loading: Coil 3 resolves `content://` URIs directly; `VideoFrameDecoder` (registered in `UnrolledApp`) renders video posters.

## Deletion

Swipe-left stages photo ids into `state.deletedPhotoIDs`. The red footer of the review sheet fires `MediaStore.createTrashRequest(...)`; the composable launches the returned `IntentSender`. The system dialog is the only confirmation step. `RESULT_CANCELED` is silent (no error banner); real failures show a localized message.

## Adding a new feature

1. `FooState.kt`: data class, immutable, sensible defaults, alphabetised.
2. `FooIntent.kt`: sealed interface; `data object` for events without payload, `data class` for events carrying data.
3. `FooViewModel.kt`: private `MutableStateFlow<FooState>`, public `StateFlow<FooState>`, `fun onIntent(intent: FooIntent) = when (intent) { ... }`. Cancellable jobs as `private var fooJob: Job? = null`.
4. `FooScreen.kt`: thin top-level wrapper that collects state, plus `FooScreenContent(state, onIntent)` that's pure.
5. Tests in the same commit.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`SwipeViewModelTest.kt` covers permission flow, load errors, piles, trash resolution from persisted ids, silent refresh, drag mechanics, undo edge cases (including group entries), trash flow (success, error, cancel), start over vs reset progress, shuffle position restoration, session counters, stats and goal celebration, similar groups, settings. `ActivityTrackerTest.kt` and `SimilarityGrouperTest.kt` cover the pure logic. Uses `StandardTestDispatcher` + `advanceTimeBy` for animation timing and `Turbine` for state-flow validation.

UI / snapshot tests and instrumented MediaStore tests are not set up yet.

## Release builds & signing

```bash
./gradlew :app:bundleRelease   # app/build/outputs/bundle/release/app-release.aab
```

The release `signingConfig` reads three Gradle properties:

```properties
# ~/.gradle/gradle.properties, never commit this
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

The keystore lives at `android/keystore.jks` (gitignored). If any property is missing, the release config falls back to the **debug** key: handy for local smoke tests, useless for Play. Play App Signing re-signs server-side in production.

R8 is enabled with `isMinifyEnabled = true` + `isShrinkResources = true`. `proguard-rules.pro` only keeps enum reflection; Compose / Coil / AndroidX ship their own consumer rules. Watch the Play Console pre-launch report for any reflection fallout.

## Releases

Every Play release **must** prepend a new block to the root [`../RELEASE_NOTES.md`](../RELEASE_NOTES.md):

- `#### vX.Y.Z (versionCode N)` header.
- Per-locale bodies inside `<en-US>...</en-US>`, `<fr-FR>...</fr-FR>`, `<nl-NL>...</nl-NL>` (500 chars max each).
- Wrap the whole block in a triple-backtick fence so markdown previews don't strip the locale tags.
- Keep all three locales in sync.
- Paste the block verbatim (tags included) into Play Console's "What's new" field.

Google Play requires `targetSdk >= 36` for new apps and updates since 31 August 2026.

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Why | Min SDK |
| --- | --- | --- |
| `READ_MEDIA_IMAGES` | Read photos for the swipe deck | 33+ |
| `READ_MEDIA_VIDEO` | Read videos for the swipe deck | 33+ |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Detect "selected photos only" mode | 34+ |
| `READ_EXTERNAL_STORAGE` (with `maxSdkVersion="32"`) | Pre-API-33 fallback | 26 to 32 |
| `ACCESS_MEDIA_LOCATION` | Read the EXIF GPS of a photo to show a place name (toggle in Settings) | 29+ |
| `POST_NOTIFICATIONS` | Daily reminder, requested only when the user enables it | 33+ |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule the reminder alarm after a reboot | 26 |

`backup_rules.xml` and `data_extraction_rules.xml` exclude all domains.

## Gotchas

- **targetSdk 37** was set to the latest platform per the dependency policy; the Android 17 behaviour changes have not been exercised on a device yet. Review them before the next release.
- **Gradle 9.7.1, not 9.8.x**: Gradle 9.8 deprecates `Configuration.setVisible`, which AGP 9.4.1 still calls at configure time, so 9.8 prints a deprecation warning on every build. Bump Gradle again once AGP stops calling it. The `AndroidGradlePluginVersion` lint check is disabled for the same reason.
- **"Unable to strip libandroidx.graphics.path.so"** at assemble time means no NDK is installed; install one through the SDK manager to silence it (cosmetic).
- **Namespace + applicationId aligned to `dev.rahier.unrolled`**: both `R`-class package and Play Store package name match.
