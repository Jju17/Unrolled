# iOS rules (SwiftUI + TCA)

Read `ios/README.md` first; this file holds the hard rules reviewers enforce.

- One `@Reducer` per feature, `@ObservableState` state with alphabetised fields, actions named after the user gesture or the effect outcome (`deleteButtonTapped`, `photosLoaded`), never after reducer internals.
- Every platform framework (PhotoKit, UserDefaults, URL opening) goes through a `@Dependency` client with `liveValue`, `testValue` and `previewValue`. Feature reducers import `ComposableArchitecture` and `Foundation` only.
- Long-running effects carry a `CancelID`; fetches use `cancelInFlight: true` so a newer request always wins.
- `@State` in views is for animation and presentation only. Anything a test would assert on lives in `State`.
- No force unwraps in production code. No `.catch` that swallows a failure silently.
- Persisted lists (`keptPhotoIDs`, `deletedPhotoIDs`) are sets in spirit: a photo lives in at most one of them, the last decision wins. `history` references photo IDs, never indexes.
- Every reducer change ships with a `TestStore` test in `UnrolledTests/SwipeFeatureTests.swift` in the same commit.
- User-visible strings go through the string catalog, translated into every language of the catalog in the same change (`StringCatalogTests` fails otherwise). No em dashes, no unicode arrows in copy.
- Regenerate the project with `xcodegen generate` after touching files; the `.xcodeproj` is never edited by hand.
- `history` entries hold one or more `PhotoDecision`s; undo restores the whole entry.
- Pure logic (`ActivityTracker`, `SimilarityGrouper`) lives in `Models/` with no framework import and its own test file.
- Byte sizes, hashes and piles come from `PhotoLibraryClient`; stats persist through `StatsClient`, reminders go through `NotificationClient`. Views format bytes and durations, state never does.
