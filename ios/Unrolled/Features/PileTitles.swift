//
//  PileTitles.swift
//  Unrolled
//

import Foundation

enum PileTitles {
    static func title(for selector: PileSelector, name: String?) -> String {
        switch selector {
        case .all: String(localized: "All photos")
        case .album: name ?? String(localized: "Album")
        case .month(let year, let month): monthTitle(year: year, month: month)
        case .onThisDay: String(localized: "On this day")
        case .oneYearAgo: String(localized: "A year ago")
        case .screenshots: String(localized: "Screenshots")
        case .videos: String(localized: "Videos")
        }
    }

    static func isCurrentMonth(_ selector: PileSelector, now: Date = Date()) -> Bool {
        guard case .month(let year, let month) = selector else { return false }
        let parts = Calendar.current.dateComponents([.year, .month], from: now)
        return parts.year == year && parts.month == month
    }

    static func bytes(_ count: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: count, countStyle: .file)
    }

    static func duration(_ seconds: TimeInterval) -> String {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = seconds >= 3600 ? [.hour, .minute] : [.minute, .second]
        formatter.unitsStyle = .abbreviated
        return formatter.string(from: max(0, seconds)) ?? "0s"
    }

    static func clock(_ seconds: Double) -> String {
        let total = Int(seconds.rounded())
        let minutes = total / 60
        let remainder = total % 60
        return String(format: "%d:%02d", minutes, remainder)
    }

    private static func monthTitle(year: Int, month: Int) -> String {
        var parts = DateComponents()
        parts.year = year
        parts.month = month
        parts.day = 1
        guard let date = Calendar.current.date(from: parts) else { return "\(year)" }
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMMM yyyy")
        return formatter.string(from: date).capitalized
    }
}
