# iOS — Unrolled

SwiftUI app. The Xcode project is generated from `project.yml` via [XcodeGen](https://github.com/yonaskolb/XcodeGen).

## First-time setup

```bash
brew install xcodegen
cd ios
xcodegen generate
open Unrolled.xcodeproj
```

The generated `.xcodeproj` is gitignored — regenerate after adding/removing source files or editing `project.yml`.

## Layout

- `Unrolled/App/` — `UnrolledApp.swift`, `AppDelegate.swift`
- `Unrolled/Features/` — views / feature modules
- `Unrolled/Models/` — data types
- `Unrolled/Resources/` — assets, fonts, localisations
- `Unrolled/Info.plist`
