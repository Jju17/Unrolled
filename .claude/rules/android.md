# Android rules (Compose + MVI)

Read `android/README.md` first; this file holds the hard rules reviewers enforce.

- One screen = immutable `State` data class (alphabetised fields), sealed `Intent` (alphabetised cases, named after user actions or effect outcomes) and a `ViewModel` exposing `StateFlow<State>`. Composables are `(state, onIntent)` and hold no business state.
- `ViewModel`s never see `Activity` or `Context`; platform APIs live behind repository interfaces injected through the `Factory`, with fakes in `app/src/test`.
- `viewModelScope` jobs are stored in `private var fooJob: Job?` and cancelled before a new load of the same kind starts.
- Persisted lists (`keptPhotoIDs`, `deletedPhotoIDs`) are sets in spirit: a photo lives in at most one of them, the last decision wins. `history` references photo IDs, never indexes.
- A resume never rebuilds the deck under the user: the first load shows the spinner, later ones are silent refreshes that keep the top card.
- Every overlay intercepts the system back button with `BackHandler`; every list that can grow is lazy.
- Touch targets are at least 48 dp, every icon-only control has a `contentDescription`, insets come from `WindowInsets`, never hardcoded.
- Every ViewModel change ships with a test in `SwipeViewModelTest.kt` in the same commit; no `android.util.Log` reached from unit-tested code without an injectable seam.
- Strings live in `values/strings.xml` and every `values-<locale>/strings.xml`, added together (`StringsParityTest` fails otherwise). Plural quantities follow each locale's CLDR set, lint rejects a wrong set. No em dashes, no unicode arrows in copy.
- Dependencies: highest stable version that keeps `./gradlew :app:testDebugUnitTest :app:lintDebug` green and warning-free; a blocked bump is recorded in `known-issues.md`.
- `history` entries hold one or more `PhotoDecision`s; undo restores the whole entry.
- Pure logic (`ActivityTracker`, `SimilarityGrouper`) has no Android import and its own test file.
- Sizes, hashes and piles come from `PhotoRepository`; stats persist through `StatsRepository`, reminders through `ReminderScheduler`. Composables format bytes and durations, state never does.
