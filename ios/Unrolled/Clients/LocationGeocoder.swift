//
//  LocationGeocoder.swift
//  Unrolled
//

import CoreLocation
import Foundation

/// Reverse-geocodes coordinates to a human-readable place name and
/// caches the result. Designed for the photo card overlay, so:
///
/// - We round the coordinate to ~110 m before keying the cache —
///   neighbouring shots from the same trip resolve once.
/// - `CLGeocoder` is rate-limited (~1 req/s per process); the actor
///   serializes calls so a fast scroll through the stack doesn't blow
///   the limit and produce spurious failures.
/// - On any error (including throttling) we cache `nil` so we don't
///   immediately retry the same coordinate.
actor LocationGeocoder {
    static let shared = LocationGeocoder()

    private var cache: [Int64: String?] = [:]
    private let geocoder = CLGeocoder()

    func placeName(latitude: Double, longitude: Double) async -> String? {
        let key = cacheKey(lat: latitude, lng: longitude)
        if let cached = cache[key] {
            return cached
        }
        let location = CLLocation(latitude: latitude, longitude: longitude)
        do {
            let placemarks = try await geocoder.reverseGeocodeLocation(location)
            let name = placemarks.first.flatMap { Self.format($0) }
            cache[key] = name
            return name
        } catch {
            cache[key] = .some(nil)
            return nil
        }
    }

    /// Quantize lat/lng to ~110 m grid (4 decimals) and pack into a
    /// single Int64 so dictionary lookup is O(1) without allocating a
    /// composite key.
    private func cacheKey(lat: Double, lng: Double) -> Int64 {
        let scale = 10_000.0
        let qLat = Int64((lat * scale).rounded())
        let qLng = Int64((lng * scale).rounded())
        return qLat &* 1_000_000 &+ qLng
    }

    /// Format the placemark as `"Locality, Country"`, falling back
    /// progressively if either component is missing.
    private static func format(_ pm: CLPlacemark) -> String? {
        let locality = pm.locality ?? pm.subAdministrativeArea
        let country = pm.country
        switch (locality, country) {
        case let (l?, c?): return "\(l), \(c)"
        case let (l?, nil): return l
        case let (nil, c?): return c
        case (nil, nil):   return pm.name
        }
    }
}
