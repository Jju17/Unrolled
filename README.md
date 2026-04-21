# PicSwipe

Monorepo layout:

- `ios/` — SwiftUI app (generate Xcode project with `xcodegen generate` from `ios/`)
- `android/` — Kotlin + Jetpack Compose app (`./gradlew :app:assembleDebug` from `android/`)
- `web/` — Vite + React + Firebase app (`npm install && npm run dev` from `web/`)
- `functions/` — Firebase Cloud Functions TypeScript (`npm install && npm run build` from `functions/`)

Root holds Firebase project config: `.firebaserc`, `firebase.json`, `firestore.rules`, `firestore.indexes.json`.
