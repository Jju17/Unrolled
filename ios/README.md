# iOS — PicSwipe

SwiftUI app. The Xcode project is generated from `project.yml` via [XcodeGen](https://github.com/yonaskolb/XcodeGen).

## First-time setup

```bash
brew install xcodegen
cd ios
xcodegen generate
open PicSwipe.xcodeproj
```

The generated `.xcodeproj` is gitignored — regenerate after adding/removing source files or editing `project.yml`.

## Layout

- `PicSwipe/App/` — `PicSwipeApp.swift`, `AppDelegate.swift`
- `PicSwipe/Features/` — views / feature modules
- `PicSwipe/Models/` — data types
- `PicSwipe/Resources/` — assets, fonts, localisations
- `PicSwipe/Info.plist`
