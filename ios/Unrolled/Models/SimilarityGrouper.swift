//
//  SimilarityGrouper.swift
//  Unrolled
//

import Foundation

/// Groups near-duplicate shots: a time-adjacent run is a candidate, a
/// perceptual hash (dHash on a 9x8 grayscale thumbnail) confirms it.
enum SimilarityGrouper {
    static let hashWidth = 9
    static let hashHeight = 8

    static func candidateRuns(
        photos: [PhotoAsset], maxGapSeconds: TimeInterval = 10, maxRunSize: Int = 6
    ) -> [[String]] {
        var runs: [[String]] = []
        var current: [String] = []
        var previousDate: Date?
        for photo in photos where !photo.isVideo {
            guard let date = photo.creationDate else {
                flush(&runs, &current)
                previousDate = nil
                continue
            }
            if let previousDate,
               abs(previousDate.timeIntervalSince(date)) <= maxGapSeconds,
               current.count < maxRunSize {
                current.append(photo.id)
            } else {
                flush(&runs, &current)
                current = [photo.id]
            }
            previousDate = date
        }
        flush(&runs, &current)
        return runs
    }

    static func dHash(gray: [UInt8]) -> UInt64 {
        guard gray.count == hashWidth * hashHeight else { return 0 }
        var hash: UInt64 = 0
        for row in 0..<hashHeight {
            for column in 0..<(hashWidth - 1) {
                let index = row * hashWidth + column
                hash <<= 1
                if gray[index] > gray[index + 1] {
                    hash |= 1
                }
            }
        }
        return hash
    }

    static func hammingDistance(_ a: UInt64, _ b: UInt64) -> Int {
        (a ^ b).nonzeroBitCount
    }

    static func confirmGroups(
        run: [String], hashes: [String: UInt64], maxDistance: Int = 12
    ) -> [[String]] {
        guard run.allSatisfy({ hashes[$0] != nil }) else { return [] }
        var groups: [[String]] = []
        var current: [String] = []
        var leadHash: UInt64 = 0
        for id in run {
            guard let hash = hashes[id] else { return [] }
            if current.isEmpty {
                current = [id]
                leadHash = hash
            } else if hammingDistance(leadHash, hash) <= maxDistance {
                current.append(id)
            } else {
                if current.count >= 2 { groups.append(current) }
                current = [id]
                leadHash = hash
            }
        }
        if current.count >= 2 { groups.append(current) }
        return groups
    }

    private static func flush(_ runs: inout [[String]], _ current: inout [String]) {
        if current.count >= 2 { runs.append(current) }
        current = []
    }
}
