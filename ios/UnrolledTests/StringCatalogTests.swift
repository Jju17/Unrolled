//
//  StringCatalogTests.swift
//  UnrolledTests
//

import XCTest

final class StringCatalogTests: XCTestCase {
    private static let languages = [
        "ar", "bn", "de", "es", "fr", "hi", "id", "it", "ja", "ko", "mr", "nl",
        "pt-BR", "ru", "ta", "te", "tr", "ur", "vi", "zh-Hans", "zh-Hant",
    ]
    private static let placeholder = try! NSRegularExpression(pattern: "%(?:\\d+\\$)?(?:lld|(?:\\.\\d+)?[@dfs])")

    private struct Catalog: Decodable {
        struct Entry: Decodable {
            struct Localization: Decodable {
                struct Unit: Decodable { let value: String }
                let stringUnit: Unit
            }
            let localizations: [String: Localization]?
            let shouldTranslate: Bool?
        }
        let strings: [String: Entry]
    }

    private func load(_ name: String) throws -> Catalog {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Unrolled/Resources/\(name)")
        return try JSONDecoder().decode(Catalog.self, from: Data(contentsOf: url))
    }

    private func placeholders(_ text: String) -> [String] {
        Self.placeholder.matches(in: text, range: NSRange(text.startIndex..., in: text))
            .map { String(text[Range($0.range, in: text)!]) }
            .map { $0.replacingOccurrences(of: "^%\\d+\\$", with: "%", options: .regularExpression) }
            .sorted()
    }

    private func check(_ catalog: Catalog, skipping skipped: Set<String> = []) {
        for (key, entry) in catalog.strings where !skipped.contains(key) && entry.shouldTranslate != false {
            let english = entry.localizations?["en"]?.stringUnit.value ?? key
            for language in Self.languages {
                guard let value = entry.localizations?[language]?.stringUnit.value else {
                    XCTFail("\(language) is missing for \"\(key)\""); continue
                }
                XCTAssertFalse(value.trimmingCharacters(in: .whitespaces).isEmpty, "\(language) is empty for \"\(key)\"")
                XCTAssertFalse(value.contains("\u{2014}"), "\(language) uses an em dash in \"\(key)\"")
                XCTAssertEqual(placeholders(value), placeholders(english), "\(language) placeholders differ for \"\(key)\"")
            }
        }
    }

    func test_everyKeyIsTranslatedInEveryLanguage() throws {
        let catalog = try load("Localizable.xcstrings")
        XCTAssertGreaterThan(catalog.strings.count, 100)
        check(catalog)
    }

    func test_photoPermissionSentenceIsTranslatedInEveryLanguage() throws {
        check(try load("InfoPlist.xcstrings"), skipping: ["CFBundleDisplayName", "CFBundleName"])
    }
}
