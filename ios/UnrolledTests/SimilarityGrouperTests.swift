//
//  SimilarityGrouperTests.swift
//  UnrolledTests
//

import XCTest

final class SimilarityGrouperTests: XCTestCase {

    func test_candidateRuns_groupsShotsWithinTheGap() {
        let photos: [PhotoAsset] = [
            .shot("a", at: 1000), .shot("b", at: 1005), .shot("c", at: 1012),
            .shot("d", at: 2000),
            .shot("e", at: 3000), .shot("f", at: 3003),
        ]
        XCTAssertEqual(SimilarityGrouper.candidateRuns(photos: photos), [["a", "b", "c"], ["e", "f"]])
    }

    func test_candidateRuns_skipsVideosAndUndatedPhotos() {
        let photos: [PhotoAsset] = [
            .shot("a", at: 1000), .shot("v", at: 1002, isVideo: true), .shot("b", at: 1004),
            PhotoAsset(id: "undated"), .shot("c", at: 1006),
        ]
        XCTAssertEqual(SimilarityGrouper.candidateRuns(photos: photos), [["a", "b"]])
    }

    func test_candidateRuns_capsRunSize() {
        let photos = (0..<8).map { PhotoAsset.shot("p\($0)", at: Double($0)) }
        XCTAssertEqual(
            SimilarityGrouper.candidateRuns(photos: photos, maxRunSize: 3),
            [["p0", "p1", "p2"], ["p3", "p4", "p5"], ["p6", "p7"]]
        )
    }

    func test_candidateRuns_emptyInput() {
        XCTAssertEqual(SimilarityGrouper.candidateRuns(photos: []), [])
        XCTAssertEqual(SimilarityGrouper.candidateRuns(photos: [.shot("only", at: 1)]), [])
    }

    func test_dHash_encodesHorizontalGradients() {
        var gray = [UInt8](repeating: 0, count: 72)
        for row in 0..<8 {
            for column in 0..<9 {
                gray[row * 9 + column] = UInt8(column)
            }
        }
        XCTAssertEqual(SimilarityGrouper.dHash(gray: gray), 0)
        let descending = gray.map { 10 - $0 }
        XCTAssertEqual(SimilarityGrouper.dHash(gray: descending), UInt64.max)
        XCTAssertEqual(SimilarityGrouper.dHash(gray: [1, 2, 3]), 0)
    }

    func test_hammingDistance() {
        XCTAssertEqual(SimilarityGrouper.hammingDistance(0, 0), 0)
        XCTAssertEqual(SimilarityGrouper.hammingDistance(0b1011, 0b0001), 2)
        XCTAssertEqual(SimilarityGrouper.hammingDistance(UInt64.max, 0), 64)
    }

    func test_confirmGroups_splitsOnDistanceAndDropsSingles() {
        let hashes: [String: UInt64] = ["a": 0, "b": 0b11, "c": UInt64.max, "d": UInt64.max ^ 0b1]
        XCTAssertEqual(
            SimilarityGrouper.confirmGroups(run: ["a", "b", "c", "d"], hashes: hashes),
            [["a", "b"], ["c", "d"]]
        )
        XCTAssertEqual(SimilarityGrouper.confirmGroups(run: ["a", "c"], hashes: hashes), [])
    }

    func test_confirmGroups_waitsForMissingHashes() {
        XCTAssertEqual(SimilarityGrouper.confirmGroups(run: ["a", "b"], hashes: ["a": 0]), [])
    }
}
