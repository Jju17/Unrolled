//
//  Photo.swift
//  PicSwipe
//

import Foundation

struct Photo: Identifiable, Equatable {
    let id: Int
    let url: URL
    let location: String
    let date: String
}

enum PhotoLibrary {
    static let samples: [Photo] = [
        Photo(id: 1,  url: URL(string: "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&q=80")!, location: "Banff, CA",      date: "Mar 12, 2026"),
        Photo(id: 2,  url: URL(string: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&q=80")!, location: "Algarve, PT",    date: "Mar 14, 2026"),
        Photo(id: 3,  url: URL(string: "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=800&q=80")!, location: "Dolomites, IT",  date: "Mar 15, 2026"),
        Photo(id: 4,  url: URL(string: "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=800&q=80")!, location: "Hallstatt, AT",  date: "Mar 16, 2026"),
        Photo(id: 5,  url: URL(string: "https://images.unsplash.com/photo-1493246507139-91e8fad9978e?w=800&q=80")!, location: "Milford Sd, NZ", date: "Mar 18, 2026"),
        Photo(id: 6,  url: URL(string: "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=800&q=80")!, location: "Lofoten, NO",    date: "Mar 19, 2026"),
        Photo(id: 7,  url: URL(string: "https://images.unsplash.com/photo-1418065460487-3e41a6c84dc5?w=800&q=80")!, location: "Lac Louise, CA", date: "Mar 20, 2026"),
        Photo(id: 8,  url: URL(string: "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800&q=80")!, location: "Zermatt, CH",    date: "Mar 22, 2026"),
        Photo(id: 9,  url: URL(string: "https://images.unsplash.com/photo-1433086966358-54859d0ed716?w=800&q=80")!, location: "Plitvice, HR",   date: "Mar 23, 2026"),
        Photo(id: 10, url: URL(string: "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=800&q=80")!, location: "Yosemite, US",   date: "Mar 25, 2026"),
        Photo(id: 11, url: URL(string: "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=800&q=80")!, location: "Reykjavík, IS",  date: "Mar 26, 2026"),
        Photo(id: 12, url: URL(string: "https://images.unsplash.com/photo-1472214103451-9374bd1c798e?w=800&q=80")!, location: "Pyrénées, FR",   date: "Mar 28, 2026"),
    ]
}

struct Album: Identifiable, Equatable {
    let id: String
    let name: String
    let count: Int
    let icon: String
}

enum AlbumLibrary {
    static let all: [Album] = [
        Album(id: "recents",   name: "Recents",     count: 2048, icon: "🕐"),
        Album(id: "favorites", name: "Favorites",   count: 156,  icon: "♡"),
        Album(id: "trip",      name: "Europe 2026", count: 342,  icon: "✈︎"),
        Album(id: "screens",   name: "Screenshots", count: 891,  icon: "▢"),
        Album(id: "selfies",   name: "Selfies",     count: 73,   icon: "☺"),
        Album(id: "videos",    name: "Videos",      count: 124,  icon: "▶"),
        Album(id: "hidden",    name: "Hidden",      count: 8,    icon: "⦸"),
    ]
}
