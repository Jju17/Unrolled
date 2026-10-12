# Unrolled

> **Swipe your gallery clean.** A Tinder-style photo-library cleaner: swipe right to keep, left to delete. Native iOS, native Android, and a small marketing/legal web surface, all from the same monorepo.

## What this repo contains

```
ios/         SwiftUI + The Composable Architecture (TCA), iOS 17+
android/     Kotlin + Jetpack Compose, MVI, minSdk 26, targetSdk 37
web/         Vite + React 19 + Tailwind 4 (privacy / support / landing)
functions/   Firebase Cloud Functions v2 (TypeScript, Node 22)
.firebaserc  Firebase project: unrolled-e4775 (region europe-west1)
firebase.json, firestore.rules, firestore.indexes.json
RELEASE_NOTES.md   single source of truth for store copy and release notes
known-issues.md    living tracker: open items and what was resolved when
.claude/rules/     architecture rules per client, loaded by Claude Code
```

Each platform is independent: you can build the iOS app without touching Gradle, and vice versa. The web and functions sub-projects share the Firebase project; neither client app calls Firebase.

## App at a glance

| | iOS | Android |
| --- | --- | --- |
| Stack | SwiftUI + TCA | Jetpack Compose + MVI |
| Min OS | iOS 17 | API 26 (Android 8) |
| Bundle / app id | `dev.rahier.unrolled` | `dev.rahier.unrolled` |
| Photo source | PhotoKit (`PHAsset`) | MediaStore (`content://` URIs) |
| Image loader | `PHImageManager` + `PHCachingImageManager` | Coil 3 (no OkHttp) |
| Deletion | `PHAssetChangeRequest.deleteAssets` (system confirm) | `MediaStore.createTrashRequest` (system confirm) |
| Place names | `CLGeocoder` (optional, Settings toggle) | `Geocoder` + `ACCESS_MEDIA_LOCATION` (optional, Settings toggle) |
| Videos | `PHAsset` `.video` + `AVKit` | `MediaStore.Files` `MEDIA_TYPE` + `VideoView`, Coil video decoder |
| Reminder | `UserNotifications` calendar trigger | `AlarmManager` + `BroadcastReceiver` |
| Tests | XCTest + `TestStore` reducer suite | JUnit + Turbine ViewModel suite |

## How the deck works (both platforms)

- The user sorts one **pile** at a time: this month, on this day (same day in earlier years), a year ago, screenshots, videos, one of the last 24 calendar months, or an album. Every pile knows its size and how many photos are left; the picker shows a progress bar per row. `.all` is the whole library.
- The deck is "the work left to do": on load, photos already present in the persisted `keptPhotoIDs` / `deletedPhotoIDs` lists are filtered out. Photos classified during the session stay in the deck so undo can bring them back.
- A photo lives in at most one list; re-classifying it moves it (last decision wins). `history` holds entries of one or more decisions referencing photo IDs, never positions; undoing an entry restores every decision in it.
- The trash sheet shows `trashPhotos`, resolved by ID from the persisted list, so it works after a relaunch or a pile switch. IDs whose asset no longer exists are pruned on load.
- Coming back to the app refreshes the library silently: vanished photos leave the deck, new ones join at the end, the top card stays the top card. Only the first load shows a spinner.
- "Start over" on the finished-pile view forgets the decisions taken on that pile's photos and reloads it. "Reset" in the trash sheet forgets everything.
- Shuffle works on the photos still undecided; turning it off resumes at the next undecided photo in chronological order.
- **Session**: the first decision after a deck build starts a session, the decision that empties the deck finishes it. The finished-pile view shows reviewed, kept, deleted, bytes to free, duration and speed, with confetti and a success haptic.
- **Similar photos**: consecutive photos shot within 10 seconds form candidate runs; a 64-bit dHash computed on-device from a 9x8 thumbnail confirms groups (Hamming distance 12 or less). The top card shows an "N similar" badge that opens a sheet to keep some and drop the rest in one entry.
- **Videos** are part of the deck by default (setting "Include videos"), with a duration badge on the card and playback in full screen. Similar detection skips them.
- **Activity stats** (`ActivityStats`, persisted locally): bytes freed and photos deleted are counted when the trash is actually emptied, photos reviewed on every decision, plus today's count, streak and milestones. The daily goal ring, streak (one forgiven day per week) and milestone cards are controlled by one setting, "Goals and streaks". The optional daily reminder is a local notification, off by default.

## Quick start per sub-project

```bash
# iOS
brew install xcodegen        # one-time
cd ios && xcodegen generate && open Unrolled.xcodeproj

# Android (uses the Gradle toolchain JDK declared in gradle/gradle-daemon-jvm.properties)
cd android && ./gradlew :app:assembleDebug

# Web
cd web && npm install && npm run dev          # http://localhost:5173

# Cloud Functions
cd functions && npm install && npm run build
```

Each sub-project has its own README with build / test / deploy details:
- [`ios/README.md`](ios/README.md)
- [`android/README.md`](android/README.md)
- [`web/README.md`](web/README.md)
- [`functions/README.md`](functions/README.md)

## Firebase project layout

The whole repo is wired to a single Firebase project, `unrolled-e4775` (region `europe-west1`).

| Service | Used for | File |
| --- | --- | --- |
| Hosting | Marketing site + Privacy/Support pages | `web/dist` (built by `web/`), see `firebase.json` |
| Functions | Reserved for server-side helpers (currently a single `ping`) | `functions/` |
| Firestore | Schema reserved; **all reads/writes denied** by `firestore.rules` until needed | `firestore.rules`, `firestore.indexes.json` |

The mobile clients do **not** initialize Firebase. The web app does not ship the Firebase SDK either; add it back the day a page needs it.

```bash
# Deploy hosting + functions
cd web && npm run build && cd ..
firebase deploy
```

## Privacy posture

- No `URLSession` / no OkHttp HTTP loader (the Android build explicitly excludes `coil-network-okhttp`).
- No analytics, no crash reporters, no third-party SDKs beyond TCA (iOS) and Coil (Android).
- The only data that can leave the device is the GPS position of the photo on screen, sent to the system reverse-geocoding service (Apple on iOS, Google on Android) to print a city name. The "Show location" setting turns it off entirely.
- Similar-photo hashes are computed in memory from 9x8 thumbnails and never stored or sent.
- Decisions (kept / staged-for-deletion photo identifiers) and activity counters are stored locally in `UserDefaults` / `SharedPreferences`, excluded from backups on Android. Reminders are local notifications; share cards contain only the app name and numbers.
- iOS ships a `PrivacyInfo.xcprivacy` declaring only `UserDefaults` (CA92.1) and `SystemBootTime` (35F9.1).
- The in-app About sheet links to the Privacy / Support pages on `unrolled-e4775.web.app` in the system browser.

## Testing

```bash
# iOS (the Unrolled scheme runs UnrolledTests; -skipMacroValidation is needed from the CLI for TCA's macros)
cd ios && xcodegen generate && xcodebuild test -project Unrolled.xcodeproj -scheme Unrolled \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -skipMacroValidation

# Android
cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug

# Web
cd web && npm run build

# Functions
cd functions && npm test
```

Every reducer / ViewModel change ships with its test in the same commit. UI and snapshot tests are not set up yet (see [`known-issues.md`](known-issues.md)).

## Dependencies

No pinned versions: every sub-project targets the highest stable version that keeps its gate green, majors included. Run `npm outdated` (web, functions), check the Gradle / AGP / Kotlin release pages (android) and the TCA releases (ios) on every pass. A bump that breaks is reverted and recorded in `known-issues.md` with the blocking version.

## Release process

Both stores get their copy from [`RELEASE_NOTES.md`](RELEASE_NOTES.md). When cutting a build:

1. Bump `versionCode` / `versionName` in `android/app/build.gradle.kts`, or `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `ios/project.yml`.
2. Prepend a new version block to the "What's new" section, with the `<en-US>`, `<fr-FR>` and `<nl-NL>` tags inside a fenced block.
3. **iOS**: archive in Xcode, upload via Organizer, paste the matching block.
4. **Android**: `./gradlew :app:bundleRelease`, upload to Play Console, paste the same block.

### Android signing

The release `signingConfig` reads from Gradle properties in `~/.gradle/gradle.properties` (`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, never committed) and the keystore at `android/keystore.jks` (gitignored). When any of them is missing the release config falls back to the debug key, which is fine for local smoke tests only.

## Repository conventions

- `.claude/rules/*.md` are the canonical architecture references for each client and are committed.
- No commits without the affected platform's gate green.
- Never edit the generated iOS `.xcodeproj`; edit `ios/project.yml` and re-run `xcodegen generate`.
- Every user-visible string lives in `Localizable.xcstrings` (iOS) or `strings.xml` + `values-fr/strings.xml` (Android), French added in the same change. No em dashes, no unicode arrows in copy.
- No force unwraps in production code.

## Ownership

Maintained by Julien Rahier. Contact: [julien@rahier.dev](mailto:julien@rahier.dev).

## License

Proprietary, all rights reserved. Source is published for transparency, not reuse.
