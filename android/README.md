# Android — Unrolled

Native Kotlin + **Jetpack Compose** app built on an **MVI** architecture. `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`. Application id and namespace both `dev.rahier.unrolled`.

## Stack

- Kotlin (latest stable)
- Jetpack Compose — BOM `2024.12.01`, Material3
- AGP 9 — note the `android.builtInKotlin=false` + `android.newDsl=false` flags in `gradle.properties` (see *Gotchas* below)
- [Coil 3](https://coil-kt.github.io/coil/) for image loading — **core only**, no `coil-network-okhttp`. The app never goes over the network; Coil resolves `content://` MediaStore URIs directly.
- JUnit 4 + Turbine for VM tests, `kotlinx-coroutines-test` for deterministic timing

No analytics, no crash reporters, no Firebase SDK. The Coil OkHttp loader is intentionally excluded — see the comment in `app/build.gradle.kts:89-93`.

## JAVA_HOME (only required for the terminal)

Android Studio finds its own JBR; `./gradlew` from a terminal does not. Export once:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Build & install

```bash
cd android
./gradlew :app:assembleDebug                      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug                       # build + push to selected device
adb shell am start -n dev.rahier.unrolled/dev.rahier.unrolled.MainActivity
```

(Open `android/` in Android Studio and the IDE handles all of this.)

## Project layout

```
app/src/main/java/dev/rahier/unrolled/
├── MainActivity.kt           — entry; constructs PhotoRepository + ViewModelProvider.Factory
├── SwipeState.kt             — immutable data class (alphabetised fields)
├── SwipeIntent.kt            — sealed interface (data object / data class)
├── SwipeViewModel.kt         — owns StateFlow<SwipeState>, reduces intents
├── SwipeScreen.kt            — top-level @Composable (collects state, owns launchers)
├── SwipeUi.kt, CardStack.kt  — main screen UI + card stack
├── ActionBar.kt, Header.kt, EmptyStates.kt, Sheets.kt, SettingsUi.kt
├── DeletedPhotosSheet.kt     — trash review sheet
├── FullscreenPhotoView.kt, ZoomableDismissable.kt, Pinchable.kt
├── PhotoAsset.kt             — value types (PhotoAsset, Album, AlbumLibrary)
└── PhotoRepository.kt        — wraps MediaStore queries

app/src/main/res/
├── values/strings.xml        — en (source of truth)
├── values-fr/strings.xml     — fr (must stay in sync, key-for-key)
├── values-night/themes.xml
├── mipmap-*/                 — adaptive icons
├── drawable/ic_launcher_background.xml
└── xml/{backup_rules,data_extraction_rules}.xml

app/src/test/java/dev/rahier/unrolled/
├── FakePhotoRepository.kt
└── SwipeViewModelTest.kt     — 50+ unit tests
```

## Architecture: MVI

Every screen is a triple — immutable `State` data class, sealed `Intent` hierarchy, `ViewModel` exposing `StateFlow<State>`. Composables are stateless: they take `(state, onIntent)` and nothing else.

```kotlin
data class SwipeState(
    val activeIndex: Int = 0,
    val dragX: Float = 0f,
    val history: List<SwipeHistoryEntry> = emptyList(),
    /* alphabetised */
)

sealed interface SwipeIntent {
    data object DeleteButtonTapped : SwipeIntent
    data class CardDragChanged(val x: Float, val y: Float) : SwipeIntent
    data class SwipeAnimationCompleted(val direction: Int) : SwipeIntent
}

class SwipeViewModel(private val photoRepository: PhotoRepository) : ViewModel() {
    private val _state = MutableStateFlow(SwipeState())
    val state: StateFlow<SwipeState> = _state.asStateFlow()
    fun onIntent(intent: SwipeIntent) { /* when (intent) { ... } */ }
}

@Composable
fun SwipeScreen(viewModel: SwipeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SwipeScreenContent(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun SwipeScreenContent(state: SwipeState, onIntent: (SwipeIntent) -> Unit) { /* pure UI */ }
```

**Hard rules** (enforced via review and via `.claude/rules/android.md`):

- Intents named after **user actions** (`DeleteButtonTapped`, `AlbumPicked(id)`), never after reducer behavior.
- Effect-completion signals are intents too (`SwipeAnimationCompleted(direction)`) so every state change is traceable through `onIntent`.
- All business state lives on the data class. `remember { mutableStateOf(...) }` inside a composable is allowed only for purely-presentational state the VM does not care about (gesture transforms, dropdown expanded flag).
- ViewModels never see `Activity` or `Context`. Permission state is passed in as a `() -> Boolean` callback.
- Density-dependent values (drag thresholds in px) come in on intents — the VM has no `LocalDensity`.
- `viewModelScope` jobs are stored as `private var fooJob: Job? = null` so a state-invalidating intent can cancel them before emitting new state.
- Alphabetise state fields, intent cases, composable parameter lists.

The full architecture playbook lives in [`.claude/rules/android.md`](../.claude/rules/android.md).

## Photo library

`PhotoRepository` (`PhotoRepository.kt`) is the only place that touches `MediaStore`. The VM holds it via constructor injection — `MainActivity` builds the `Factory`.

Permission flow (`SwipeScreen.kt`):
1. `ScreenAppeared` → VM checks `hasPermission()` → emits `permissionStatus`.
2. If not granted, the gate screen shows a button that triggers `rememberLauncherForActivityResult(RequestMultiplePermissions())`.
3. Result comes back as `PermissionResult(status)` → VM loads photos + albums in parallel.
4. The launcher requests `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (with `maxSdkVersion="32"`) at runtime based on `Build.VERSION.SDK_INT`. On API 34+, partial photo access is detected via `READ_MEDIA_VISUAL_USER_SELECTED` and surfaced to the user.
5. Permanent denial is detected via `shouldShowRequestPermissionRationale()`; the gate's button switches to "Open Settings" and launches `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.

Image loading: Coil 3 resolves `content://` URIs directly — `AsyncImage(model = photo.uri, ...)`.

## Deletion

Implemented. Swipe-left stages photo URIs into `state.deletedPhotoIDs`. When the user confirms in the trash sheet, the VM fires `MediaStore.createTrashRequest(...)`, gets back an `IntentSender`, and the composable launches it via `ActivityResultContracts.StartIntentSenderForResult`. Android shows its own system trash-confirm dialog — that's the platform's behavior, not a bug.

## Adding a new feature

1. `FooState.kt` — data class, immutable, sensible defaults, alphabetised.
2. `FooIntent.kt` — sealed interface; `data object` for events without payload, `data class` for events carrying data.
3. `FooViewModel.kt` — private `MutableStateFlow<FooState>`, public `StateFlow<FooState>`, `fun onIntent(intent: FooIntent) = when (intent) { … }`. Cancellable jobs as `private var fooJob: Job? = null`.
4. `FooScreen.kt` — thin top-level wrapper that collects state, plus `FooScreenContent(state, onIntent)` that's pure.
5. Wire into navigation when navigation arrives.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`SwipeViewModelTest.kt` covers 50+ scenarios — permission flow, load errors, drag mechanics, undo edge cases, trash flow (incl. error path), shuffle position restoration, cancellation, presentation flags. Uses `StandardTestDispatcher` + `advanceTimeBy` for animation timing and `Turbine` for state-flow validation.

UI / snapshot tests + instrumented MediaStore tests are not yet set up — see [`../audit.md`](../audit.md).

## Release builds & signing

```bash
./gradlew :app:bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
```

The release `signingConfig` reads three Gradle properties:

```properties
# ~/.gradle/gradle.properties — NEVER commit this
RELEASE_STORE_PASSWORD=…
RELEASE_KEY_ALIAS=…
RELEASE_KEY_PASSWORD=…
```

The keystore lives at `android/keystore.jks` (gitignored). If any property is missing, the release config falls back to the **debug** key — handy for local smoke tests, useless for Play Store. Play App Signing handles server-side re-signing in production.

R8 is enabled with `isMinifyEnabled = true` + `isShrinkResources = true`. `proguard-rules.pro` only ships the minimal overrides — Compose / Coil / AndroidX consumer rules are sufficient. Watch the Play Console pre-launch report for any reflection fallout.

## Releases

Every Play release **must** prepend a new block to the root [`../RELEASE_NOTES.md`](../RELEASE_NOTES.md):

- `#### vX.Y.Z (versionCode N)` header.
- Per-locale bodies inside `<en-US>…</en-US>`, `<fr-FR>…</fr-FR>`, `<nl-NL>…</nl-NL>` (≤500 chars each).
- Wrap the whole block in a triple-backtick fence so markdown previews don't strip the locale tags.
- Keep all three locales in sync — every change to `<en-US>` must update `<fr-FR>` and `<nl-NL>` in the same diff.
- Paste the block verbatim (tags included) into Play Console's "What's new" field.

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Why | Min SDK |
| --- | --- | --- |
| `READ_MEDIA_IMAGES` | Read photos for the swipe deck | 33+ |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Detect "selected photos only" mode | 34+ |
| `READ_EXTERNAL_STORAGE` (with `maxSdkVersion="32"`) | Pre-API-33 fallback | 26–32 |

`backup_rules.xml` and `data_extraction_rules.xml` exclude all domains — the app holds no app-private data outside the user's `MediaStore`.

## Gotchas

- **AGP 9 + Kotlin plugin** — AGP 9 bundles Kotlin support. `gradle.properties` sets `android.builtInKotlin=false` + `android.newDsl=false` so the explicit `org.jetbrains.kotlin.android` plugin can apply without conflict. Don't remove those lines without removing the explicit plugin too.
- **Namespace + applicationId aligned to `dev.rahier.unrolled`** — both `R`-class package and Play Store package name match. The legacy `picswipe` namespace was migrated when the brand was finalised.
