# iOS: Unrolled

Native SwiftUI app built on **The Composable Architecture (TCA)**. iOS 17+. Bundle id `dev.rahier.unrolled`. The Xcode project is generated from [`project.yml`](project.yml); never edit the `.xcodeproj` directly.

## Stack

- Swift 5.9 language mode, latest Xcode
- SwiftUI
- [The Composable Architecture](https://github.com/pointfreeco/swift-composable-architecture), SPM dependency declared in `project.yml` with a `from:` lower bound; `Package.resolved` is not committed so every `xcodegen generate` picks up the latest release
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) generates `Unrolled.xcodeproj` from `project.yml`
- PhotoKit (read + delete + limited-library picker + sizes + thumbnails for hashing), AVKit, UserNotifications and `CLGeocoder` wrapped behind TCA clients
- XCTest + `TestStore` for the reducer suite

No analytics, no third-party SDK beyond TCA. The only network traffic is the optional reverse-geocoding call (`CLGeocoder`) that prints a city name under a photo, gated by the "Show location" setting. The daily reminder is a local `UserNotifications` calendar trigger, off by default.

## First-time setup

```bash
brew install xcodegen
cd ios
xcodegen generate
open Unrolled.xcodeproj
```

The generated `.xcodeproj` is gitignored. **Re-run `xcodegen generate` after adding/removing source files or editing `project.yml`.**

## Project layout

```
Unrolled/
├── App/
│   ├── UnrolledApp.swift       @main entry
│   └── AppDelegate.swift
├── Features/                   one reducer + its views per file
│   ├── SwipeFeature.swift      root reducer (auth, piles, deck, session, stats, similar, trash, undo, refresh)
│   ├── SwipeScreen.swift       main screen (header with pile title, goal ring, toast, overlays)
│   ├── PhotoCard.swift         single card with stamps, date, place name, video and similar badges
│   ├── PhotoAssetImage.swift   progressive image loader (PHCachingImageManager)
│   ├── LivePhotoPlayer.swift   PHLivePhotoView bridge
│   ├── VideoPage.swift         AVKit player fed by PHImageManager.requestPlayerItem
│   ├── FullscreenPhotoView.swift, ZoomableScrollView.swift, Pinchable.swift
│   ├── PilePickerSheet.swift, PileTitles.swift (titles, byte and duration formatting)
│   ├── SessionSummaryView.swift, ConfettiView.swift
│   ├── StatsSheet.swift, GoalRing.swift, MilestoneOverlay.swift, ShareCardView.swift
│   ├── SimilarPhotosSheet.swift
│   ├── SettingsSheet.swift, AboutSheet.swift
│   └── DeletedPhotosSheet.swift
├── Models/                     value types, pure logic
│   ├── PhotoAsset.swift        PhotoAsset, PhotoDecision, SwipeHistoryEntry, authorization status
│   ├── Pile.swift              Pile + PileSelector
│   ├── ActivityStats.swift     ActivityStats, Milestone, ActivityTracker (streak, goal, milestones)
│   └── SimilarityGrouper.swift candidate runs, dHash, Hamming grouping
├── Clients/
│   ├── PhotoLibraryClient.swift   PhotoKit wrapper (piles, photos, sizes, hashes, delete, limited picker)
│   ├── SwipeProgressClient.swift  persisted kept / deleted ID lists
│   ├── StatsClient.swift          persisted ActivityStats
│   ├── SettingsClient.swift       persisted user settings
│   ├── NotificationClient.swift   UserNotifications daily reminder
│   └── LocationGeocoder.swift     cached CLGeocoder
├── Resources/
│   ├── Assets.xcassets         AppIcon (light/dark/tinted), AppLogo
│   ├── Localizable.xcstrings   en + fr, every key translated
│   └── InfoPlist.xcstrings
├── Info.plist
└── PrivacyInfo.xcprivacy       required Apple privacy manifest
```

`UnrolledTests/` contains the reducer suite (`SwipeFeatureTests.swift`), `ActivityTrackerTests.swift`, `SimilarityGrouperTests.swift` and shared `Fixtures.swift`.

## Architecture: TCA

Every feature is a `@Reducer` struct: `State`, `Action`, `body`. The view holds `StoreOf<Feature>`, sends actions, reads state. The hard rules live in [`.claude/rules/ios.md`](../.claude/rules/ios.md).

Deck semantics shared with Android (see the root README): the deck is one pile (`selectedPile: PileSelector`), persisted `keptPhotoIDs` / `deletedPhotoIDs` filter it on load, a photo lives in at most one list, `history` entries hold decision lists referencing photo IDs, the trash sheet renders `trashPhotos` resolved by ID, and `libraryRefreshRequested` (scene active, limited picker closed) refreshes silently while keeping the top card. Session counters (`sessionKeptIDs`, `sessionDeletedIDs`, timestamps from `@Dependency(\.date)`) feed the finished-pile summary; `ActivityTracker` updates `stats` on every decision and on trash emptying; `SimilarityGrouper` turns `similarityHashes` into `similarGroups`.

## Build and test

```
⌘B   build
⌘R   run on the selected simulator
⌘U   run all tests
```

From the CLI (the `Unrolled` scheme declares `UnrolledTests` as its test target; `-skipMacroValidation` is required outside Xcode for TCA's macros):

```bash
xcodebuild test -project Unrolled.xcodeproj -scheme Unrolled \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -skipMacroValidation
```

First build takes a few minutes (SPM fetches TCA + transitive deps); subsequent builds are fast.

## Photo library access

`PhotoLibraryClient` is the single seam between PhotoKit and feature code. Closure-shaped so tests can replace any member:

```swift
struct PhotoLibraryClient: Sendable {
    var authorizationStatus: @Sendable () -> PhotoAuthorizationStatus
    var deleteAssets: @Sendable (_ ids: [String]) async throws -> Void   // throws PhotoLibraryError.cancelled on user cancel
    var fetchFileSizes: @Sendable (_ ids: [String]) async -> [String: Int64]
    var fetchPhotos: @Sendable (_ selector: PileSelector, _ includeVideos: Bool) async -> [PhotoAsset]
    var fetchPhotosByIDs: @Sendable (_ ids: [String]) async -> [PhotoAsset]
    var fetchPiles: @Sendable (_ now: Date, _ classifiedIDs: Set<String>, _ includeVideos: Bool) async -> [Pile]
    var fetchSimilarityHashes: @Sendable (_ ids: [String]) async -> [String: UInt64]
    var presentLimitedLibraryPicker: @Sendable () async -> Void
    var requestAuthorization: @Sendable () async -> PhotoAuthorizationStatus
}
```

`liveValue` calls real PhotoKit. `testValue` returns deterministic stubs. `previewValue` returns sensible defaults for `#Preview`.

`NSPhotoLibraryUsageDescription` is defined in `project.yml` under `info.properties` and localized in `InfoPlist.xcstrings`.

`fetchFileSizes` reads `PHAssetResource` lazily (bounded lists only: trash, deleted decisions), `fetchSimilarityHashes` draws a 9x8 grayscale thumbnail per asset in memory.

Image loading happens in the `PhotoAssetImage` view through `PhotoImageCache` (a `PHCachingImageManager` plus an `NSCache` of decoded images). The view uses `.task(id: assetID)` so moving to a new card cancels the previous load; `SwipeScreen.prefetchUpcoming` warms the next 12 cards.

## Deletion

Swipe-left stages photo IDs into `state.deletedPhotoIDs` (persisted). The trash sheet shows `trashPhotos` resolved by ID; "Empty trash" fires `photoLibrary.deleteAssets(ids)` and PhotoKit shows the only confirmation dialog. A user cancel is silent, any other failure shows a banner in the sheet.

## Adding a new feature

1. `Unrolled/Features/FooFeature.swift`: `@Reducer struct FooFeature { State; Action; body }`.
2. `Unrolled/Features/FooScreen.swift`: `let store: StoreOf<FooFeature>`.
3. Compose into the parent (root or `@Presents` / `.ifLet` if presented).
4. `xcodegen generate` to regenerate the project.
5. Tests in `UnrolledTests/` in the same commit.
