# Unrolled

> **Swipe your gallery clean.** A Tinder-style photo-library cleaner: swipe right to keep, left to delete. Native iOS, native Android, and a small marketing/legal web surface — all from the same monorepo.

## What this repo contains

```
ios/         — SwiftUI + The Composable Architecture (TCA), iOS 17+
android/     — Kotlin + Jetpack Compose, MVI, minSdk 26
web/         — Vite + React 19 + Tailwind 4 (privacy / support / landing)
functions/   — Firebase Cloud Functions v2 (TypeScript, Node 22)
.firebaserc  — Firebase project: unrolled-e4775 (region europe-west1)
firebase.json, firestore.rules, firestore.indexes.json
RELEASE_NOTES.md  — single source of truth for store release notes
audit.md     — code audit & improvement backlog
```

Each platform is independent: you can build the iOS app without touching Gradle, and vice versa. The web + functions sub-projects share the Firebase project but neither client app calls Firebase today (no analytics, no networking).

## App at a glance

| | iOS | Android |
| --- | --- | --- |
| Stack | SwiftUI + TCA | Jetpack Compose + MVI |
| Min OS | iOS 17 | API 26 (Android 8) |
| Bundle / app id | `dev.rahier.unrolled` | `dev.rahier.unrolled` |
| Photo source | PhotoKit (`PHAsset`) | MediaStore (`content://` URIs) |
| Image loader | `PHImageManager` + `PHCachingImageManager` | Coil 3 (no OkHttp) |
| Deletion | `PHAssetChangeRequest.deleteAssets` (system confirm) | `MediaStore.createTrashRequest` (system confirm) |
| Tests | `swift-testing` reducer suite (777 LOC) | JUnit + Turbine VM suite (50+ tests) |
| Networking / analytics | None | None |

## Architecture in one paragraph per platform

**iOS** — Each screen is a `@Reducer struct` (state machine) + a SwiftUI view holding `StoreOf<Feature>`. Business state lives in `@ObservableState`; `@State` is reserved for view-only animation. Platform frameworks (PhotoKit, etc.) are wrapped in `Sendable` client structs with `liveValue` / `testValue` / `previewValue`, injected through `@Dependency`. Long-running effects use `CancelID`s so a follow-up action can tear them down. The Xcode project is generated from `ios/project.yml` via [XcodeGen](https://github.com/yonaskolb/XcodeGen) — the `.xcodeproj` is gitignored. Full rules in [`.claude/rules/ios.md`](.claude/rules/ios.md).

**Android** — Each screen is a triple: an immutable `State` data class, a sealed `Intent` hierarchy, and a `ViewModel` that exposes `StateFlow<State>` and reduces intents into new states. Composables are stateless `(state, onIntent)`. Platform APIs are wrapped in `Repository` classes injected via a `ViewModelProvider.Factory`. `viewModelScope` owns side effects with cancellable `Job?`s. Density-dependent values come in on intents — the VM never touches `LocalDensity`. Full rules in [`.claude/rules/android.md`](.claude/rules/android.md).

## Quick start per sub-project

```bash
# iOS
brew install xcodegen        # one-time
cd ios && xcodegen generate && open Unrolled.xcodeproj

# Android (needs Android Studio's bundled JBR if no system JDK)
cd android && ./gradlew :app:assembleDebug

# Web
cd web && npm install && npm run dev          # → http://localhost:5173

# Cloud Functions
cd functions && npm install && npm run build
```

Each sub-project has its own README with build / test / deploy details:
- [`ios/README.md`](ios/README.md)
- [`android/README.md`](android/README.md)
- [`web/README.md`](web/README.md)
- [`functions/README.md`](functions/README.md)

## Firebase project layout

The whole repo is wired to a single Firebase project — `unrolled-e4775` (region `europe-west1`).

| Service | Used for | File |
| --- | --- | --- |
| Hosting | Marketing site + Privacy/Support pages | `web/dist` (built by `web/`) → `firebase.json` |
| Functions | Reserved for server-side helpers (currently a single `ping`) | `functions/` → `firebase.json` |
| Firestore | Schema reserved; **all reads/writes denied** by `firestore.rules` until needed | `firestore.rules`, `firestore.indexes.json` |

The mobile clients do **not** initialize Firebase today — privacy-first by default. The web app fills in the public Firebase config in `web/src/firebase.ts` only as features ship.

```bash
# Deploy hosting + functions
cd web && npm run build && cd ..
firebase deploy
```

## Privacy posture

The mobile apps are deliberately offline:
- No `URLSession` / no OkHttp HTTP loader — the Android build explicitly excludes `coil-network-okhttp` to keep the Data Safety story clean.
- No analytics / no crash reporters / no third-party SDKs.
- iOS ships a `PrivacyInfo.xcprivacy` declaring only `UserDefaults` (CA92.1) and `SystemBootTime` (35F9.1).
- Android `data_extraction_rules.xml` and `backup_rules.xml` exclude all domains; no app-private state to worry about.
- The only external touchpoints are `Link`s in the in-app About sheet that open the Privacy / Support pages on `unrolled-e4775.web.app` in the system browser.

## Testing

- **iOS** — `cmd+U` from Xcode, or `xcodebuild test -scheme Unrolled` from CLI. The reducer suite in `ios/UnrolledTests/SwipeFeatureTests.swift` exercises every action and is the regression net.
- **Android** — `./gradlew :app:testDebugUnitTest` from `android/`. `SwipeViewModelTest.kt` uses `StandardTestDispatcher` + `Turbine` for deterministic timing.
- **Functions** — `cd functions && npm test` (Vitest).

UI / snapshot tests are not yet set up on either client — see [`audit.md`](audit.md) for the prioritized polish backlog.

## Release process

Both stores get release notes from a single file: [`RELEASE_NOTES.md`](RELEASE_NOTES.md). When cutting a build:

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`, or `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `ios/project.yml`.
2. Prepend a new version block to `RELEASE_NOTES.md`'s "What's new" section. Use the `<en-US>…</en-US>` / `<fr-FR>…</fr-FR>` / `<nl-NL>…</nl-NL>` per-locale tags wrapped inside a triple-backtick fence so markdown previews don't strip them.
3. **iOS**: archive in Xcode, upload via Organizer; "What's New" is copy-pasted from the corresponding block in `RELEASE_NOTES.md`.
4. **Android**: build a signed `bundleRelease`, upload to Play Console, paste the same block (Play parses the locale tags).

The release-notes block must be present in all three locales — if `<en-US>` changes, update `<fr-FR>` and `<nl-NL>` in the same diff.

### Android signing

The release `signingConfig` reads from project Gradle properties:

```properties
# ~/.gradle/gradle.properties (NEVER commit)
RELEASE_STORE_PASSWORD=…
RELEASE_KEY_ALIAS=…
RELEASE_KEY_PASSWORD=…
```

The keystore lives at `android/keystore.jks` (gitignored). If any of those are missing, the release config falls back to the debug key — handy for local `assembleRelease` smoke tests, but Play uploads will be re-signed by Play App Signing in production.

## Repository conventions

- **`.claude/rules/*.md`** are the canonical architecture references for each sub-project. They get auto-loaded when working with Claude Code.
- **No commits without tests passing** on the affected platform.
- **Never** edit the generated iOS `.xcodeproj`; edit `ios/project.yml` and re-run `xcodegen generate`.
- **Strings**: every user-visible string belongs in `Localizable.xcstrings` (iOS) or `strings.xml` + `values-fr/strings.xml` (Android). Audit gaps are tracked in [`audit.md`](audit.md).
- **Force-unwraps in production code (`!`, `!!`) are forbidden** — the audit calls out the few that exist.

## Ownership

Maintained by Julien Rahier. Contact: [julien@rahier.dev](mailto:julien@rahier.dev).

## License

Proprietary — all rights reserved. Source is published for transparency, not reuse.
