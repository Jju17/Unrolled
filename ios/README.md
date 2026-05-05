# iOS — Unrolled

Native SwiftUI app built on **The Composable Architecture (TCA)**. iOS 17+. Bundle id `dev.rahier.picswipe`. The Xcode project is generated from [`project.yml`](project.yml) — never edit the `.xcodeproj` directly.

## Stack

- Swift 5.9+ / Xcode 16+
- SwiftUI
- [The Composable Architecture](https://github.com/pointfreeco/swift-composable-architecture) — declared as an SPM dependency in `project.yml`
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) — generates `Unrolled.xcodeproj` from `project.yml`
- PhotoKit (read + delete) wrapped behind a TCA `Client`
- `swift-testing` for unit tests

No third-party SDKs at runtime. No networking. No analytics.

## First-time setup

```bash
brew install xcodegen
cd ios
xcodegen generate
open Unrolled.xcodeproj
```

The generated `.xcodeproj` is gitignored. **Re-run `xcodegen generate` after adding/removing source files or editing `project.yml`** — Xcode will not pick up new files automatically.

## Project layout

```
Unrolled/
├── App/
│   ├── UnrolledApp.swift       — @main entry, sets up the root Store
│   └── AppDelegate.swift
├── Features/                   — one Feature reducer + its View per file
│   ├── SwipeFeature.swift      — root reducer (auth, library, drag, swipe, undo)
│   ├── SwipeScreen.swift       — main screen view
│   ├── PhotoCard.swift         — single card with shadow, transforms
│   ├── PhotoAssetImage.swift   — image loader (PHImageManager)
│   ├── FullscreenPhotoView.swift, ZoomableScrollView.swift, Pinchable.swift
│   ├── AlbumPickerSheet.swift, SettingsSheet.swift, AboutSheet.swift
│   └── DeletedPhotosSheet.swift
├── Models/                     — value types (PhotoAsset, Album)
├── Clients/
│   └── PhotoLibraryClient.swift  — Sendable client wrapping PhotoKit
├── Resources/
│   ├── Assets.xcassets         — AppIcon (light/dark/tinted), AppLogo
│   ├── Localizable.xcstrings   — en-US + fr-FR
│   └── InfoPlist.xcstrings
├── Info.plist
└── PrivacyInfo.xcprivacy       — required Apple privacy manifest
```

`UnrolledTests/` contains the reducer suite (`SwipeFeatureTests.swift`) and shared `Fixtures.swift`.

## Architecture: TCA

Every feature is a `@Reducer` struct — `State` (data class), `Action` (enum), `body` (reducer). The view holds `StoreOf<Feature>`, sends actions, reads state.

```swift
@Reducer
struct SwipeFeature {
    @ObservableState
    struct State: Equatable { /* alphabetised fields */ }

    enum Action {
        case deleteButtonTapped              // user-intent name
        case swipeAnimationCompleted(direction: Int)  // effect-completion intent
        // ...
    }

    enum CancelID { case swipeAnimation, fetchPhotos, fetchAlbums, emptyTrash }

    @Dependency(\.continuousClock) var clock
    @Dependency(\.photoLibrary) var photoLibrary

    var body: some Reducer<State, Action> { ... }
}
```

**Hard rules** (enforced via review and via `.claude/rules/ios.md`):

- Actions are named after the **user gesture or effect outcome**, never after the reducer's internal operation.
- `@State` is for view-local UI only. Anything a test would assert on lives in `Feature.State`.
- Platform frameworks are accessed through `@Dependency` clients — never `import Photos` from a feature reducer. (Pure rendering views like `PhotoAssetImage` are the only exception.)
- Cancellable effects always have a `CancelID`.
- No force-unwraps (`!`) in production code.
- No legacy TCA APIs: `reduce(into:)`, `ViewStore`, `WithViewStore`, `TaskResult`, `@PresentationState`, `sheet(store:)`.

The full architecture playbook lives in [`.claude/rules/ios.md`](../.claude/rules/ios.md).

## Build from Xcode

```
⌘B   build
⌘R   run on the selected simulator
⌘U   run all tests
```

## Build from CLI

```bash
xcodebuild \
  -project ios/Unrolled.xcodeproj \
  -scheme Unrolled \
  -destination 'generic/platform=iOS Simulator' \
  -configuration Debug \
  build
```

First build takes a few minutes (SPM fetches TCA + transitive deps); subsequent builds are fast.

## Run on a simulator from CLI

```bash
xcrun simctl boot "iPhone 17 Pro" && open -a Simulator
xcrun simctl install booted \
  ~/Library/Developer/Xcode/DerivedData/Unrolled-*/Build/Products/Debug-iphonesimulator/Unrolled.app
xcrun simctl launch booted dev.rahier.picswipe
```

## Photo library access

`PhotoLibraryClient` (`Clients/PhotoLibraryClient.swift`) is the single seam between PhotoKit and feature code. Closure-shaped (not protocol) so it composes naturally with `dependency(\.photoLibrary, stub)`:

```swift
struct PhotoLibraryClient: Sendable {
    var authorizationStatus: @Sendable () -> PhotoAuthorizationStatus
    var requestAuthorization: @Sendable () async -> PhotoAuthorizationStatus
    var fetchAlbums: @Sendable () async -> [Album]
    var fetchPhotos: @Sendable (_ albumID: String?) async -> [PhotoAsset]
    var deleteAssets: @Sendable (_ ids: [String]) async throws -> Void
}
```

`liveValue` calls real PhotoKit. `testValue` returns deterministic stubs. `previewValue` returns sensible defaults for `#Preview`.

The `Info.plist` key `NSPhotoLibraryUsageDescription` is required even for read-only access — it's defined in `project.yml` under `info.properties` so XcodeGen writes it into the generated `Info.plist`.

Image loading happens directly in the `PhotoAssetImage` view via `PHImageManager.default().requestImage(...)` (PhotoKit is UI-adjacent here; wrapping it in a client buys little). The view uses `.task(id: assetID)` so moving to a new card auto-cancels the previous load. A custom `actor PhotoImageCache` pre-warms `PHCachingImageManager` for the next few cards in the stack.

## Deletion

Implemented. Swipe-left stages photo IDs into `state.deletedPhotoIDs`; the user reviews them in the "Trash" sheet and confirms, which fires `photoLibrary.deleteAssets(ids)`. PhotoKit shows its own system confirmation dialog before the actual deletion — that's intentional, not a bug.

## Adding a new feature

1. `Unrolled/Features/FooFeature.swift` — `@Reducer struct FooFeature { State; Action; body }`.
2. `Unrolled/Features/FooScreen.swift` — `let store: StoreOf<FooFeature>`.
3. Compose into the parent (root or `@Presents`/`.ifLet` if presented).
4. `xcodegen generate` to regenerate the project.

## Testing

```bash
xcodebuild -project ios/Unrolled.xcodeproj -scheme Unrolled \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

`UnrolledTests/SwipeFeatureTests.swift` is the regression net — auth flow, drag mechanics, undo edge cases (negative-clamp guard), trash empty (success + error path), shuffle position restoration, cancellation, computed properties, presentation flags. Add a test next to the production change, not as a follow-up.

UI / snapshot coverage is not set up yet — see [`../audit.md`](../audit.md) for the polish backlog.

## Privacy

- `PrivacyInfo.xcprivacy` declares **no** data collection, **no** tracking, **no** tracking domains. The two declared API accesses are `UserDefaults` (CA92.1) and `SystemBootTime` (35F9.1, used by TCA's `continuousClock`).
- No `URLSession` or third-party network calls.
- `Info.plist`'s `ITSAppUsesNonExemptEncryption` is `false`.

## Releases

Bumping `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `project.yml` requires regenerating the project. The "What's New" copy must be added to the root [`../RELEASE_NOTES.md`](../RELEASE_NOTES.md) — never create a parallel iOS-only release-notes file.
