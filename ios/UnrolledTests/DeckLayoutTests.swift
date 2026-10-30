//
//  DeckLayoutTests.swift
//  UnrolledTests
//

import XCTest

final class DeckLayoutTests: XCTestCase {

    func test_cardSize_fillsAPhoneSizedArea() {
        let size = DeckLayout.cardSize(fitting: CGSize(width: 353, height: 600))
        XCTAssertEqual(size, CGSize(width: 353, height: 600))
    }

    func test_cardSize_capsTheWidthOnAWideInnerDisplay() {
        let size = DeckLayout.cardSize(fitting: CGSize(width: 1000, height: 500))
        XCTAssertEqual(size.height, 500)
        XCTAssertEqual(size.width, 425, accuracy: 0.001)
    }

    func test_cardSize_keepsAStrictlyPortraitAreaUntouched() {
        let size = DeckLayout.cardSize(fitting: CGSize(width: 500, height: 700))
        XCTAssertEqual(size, CGSize(width: 500, height: 700))
    }

    func test_cardSize_neverGoesNegative() {
        XCTAssertEqual(DeckLayout.cardSize(fitting: CGSize(width: -10, height: -10)), .zero)
        XCTAssertEqual(DeckLayout.cardSize(fitting: .zero), .zero)
        XCTAssertEqual(DeckLayout.cardSize(fitting: CGSize(width: 300, height: 0)), CGSize(width: 300, height: 0))
    }

    func test_maxColumnWidth_isWiderThanAnyPhone() {
        XCTAssertGreaterThan(DeckLayout.maxColumnWidth, 440)
    }

    private let landscape = CGRect(x: 0, y: 0, width: 1017, height: 699)
    private let portrait = CGRect(x: 0, y: 0, width: 699, height: 1017)

    func test_foldedPanes_horizontalFold_putsContentAboveAndControlsBelow() {
        let fold = CGRect(x: 0, y: 329, width: 1017, height: 40)
        let panes = DeckLayout.foldedPanes(in: landscape, fold: fold)
        XCTAssertEqual(panes?.orientation, .horizontal)
        XCTAssertEqual(panes?.content, CGRect(x: 0, y: 0, width: 1017, height: 329))
        XCTAssertEqual(panes?.controls, CGRect(x: 0, y: 369, width: 1017, height: 330))
    }

    func test_foldedPanes_verticalFold_putsContentLeadingAndControlsTrailing() {
        let fold = CGRect(x: 329, y: 0, width: 40, height: 1017)
        let panes = DeckLayout.foldedPanes(in: portrait, fold: fold)
        XCTAssertEqual(panes?.orientation, .vertical)
        XCTAssertEqual(panes?.content, CGRect(x: 0, y: 0, width: 329, height: 1017))
        XCTAssertEqual(panes?.controls, CGRect(x: 369, y: 0, width: 330, height: 1017))
    }

    func test_foldedPanes_clipsAFoldThatOverflowsTheBounds() {
        let fold = CGRect(x: -100, y: 329, width: 2000, height: 40)
        let panes = DeckLayout.foldedPanes(in: landscape, fold: fold)
        XCTAssertEqual(panes?.content.width, 1017)
        XCTAssertEqual(panes?.controls.minY, 369)
    }

    func test_foldedPanes_isNilWhenTheFoldMissesTheBounds() {
        XCTAssertNil(DeckLayout.foldedPanes(in: landscape, fold: CGRect(x: 0, y: 800, width: 1017, height: 40)))
        XCTAssertNil(DeckLayout.foldedPanes(in: landscape, fold: .zero))
        XCTAssertNil(DeckLayout.foldedPanes(in: landscape, fold: .null))
    }

    func test_foldedPanes_isNilWhenAPaneWouldBeTooSmall() {
        let nearTheTop = CGRect(x: 0, y: 60, width: 1017, height: 40)
        XCTAssertNil(DeckLayout.foldedPanes(in: landscape, fold: nearTheTop))
        let nearTheEdge = CGRect(x: 600, y: 0, width: 40, height: 1017)
        XCTAssertNil(DeckLayout.foldedPanes(in: portrait, fold: nearTheEdge))
    }
}
