package dev.rahier.picswipe

data class Photo(
    val id: Int,
    val url: String,
    val location: String,
    val date: String
)

data class Album(
    val id: String,
    val name: String,
    val count: Int,
    val icon: String
)

object PhotoLibrary {
    val samples = listOf(
        Photo(1,  "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&q=80", "Banff, CA",      "Mar 12, 2026"),
        Photo(2,  "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&q=80", "Algarve, PT",    "Mar 14, 2026"),
        Photo(3,  "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=800&q=80", "Dolomites, IT",  "Mar 15, 2026"),
        Photo(4,  "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=800&q=80", "Hallstatt, AT",  "Mar 16, 2026"),
        Photo(5,  "https://images.unsplash.com/photo-1493246507139-91e8fad9978e?w=800&q=80", "Milford Sd, NZ", "Mar 18, 2026"),
        Photo(6,  "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=800&q=80", "Lofoten, NO",    "Mar 19, 2026"),
        Photo(7,  "https://images.unsplash.com/photo-1418065460487-3e41a6c84dc5?w=800&q=80", "Lac Louise, CA", "Mar 20, 2026"),
        Photo(8,  "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800&q=80", "Zermatt, CH",    "Mar 22, 2026"),
        Photo(9,  "https://images.unsplash.com/photo-1433086966358-54859d0ed716?w=800&q=80", "Plitvice, HR",   "Mar 23, 2026"),
        Photo(10, "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=800&q=80", "Yosemite, US",   "Mar 25, 2026"),
        Photo(11, "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=800&q=80", "Reykjavík, IS",  "Mar 26, 2026"),
        Photo(12, "https://images.unsplash.com/photo-1472214103451-9374bd1c798e?w=800&q=80", "Pyrénées, FR",   "Mar 28, 2026")
    )
}

object AlbumLibrary {
    val all = listOf(
        Album("recents",   "Recents",     2048, "🕐"),
        Album("favorites", "Favorites",   156,  "♡"),
        Album("trip",      "Europe 2026", 342,  "✈︎"),
        Album("screens",   "Screenshots", 891,  "▢"),
        Album("selfies",   "Selfies",     73,   "☺"),
        Album("videos",    "Videos",      124,  "▶"),
        Album("hidden",    "Hidden",      8,    "⦸")
    )
}
