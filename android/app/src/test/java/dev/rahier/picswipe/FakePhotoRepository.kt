package dev.rahier.picswipe

/**
 * In-memory [PhotoRepository] for unit tests. Each load method is fully
 * controllable: default lists, per-album mappings, or throw on demand.
 *
 * Usage:
 * ```
 * val repo = FakePhotoRepository(
 *     initialHasPermission = true,
 *     albums = listOf(Album(...)),
 *     photos = listOf(PhotoAsset(...))
 * )
 * // or, to simulate a failure on the next load:
 * repo.photosError = IllegalStateException("cursor closed")
 * ```
 */
class FakePhotoRepository(
    var initialHasPermission: Boolean = true,
    var initialHasPartialAccess: Boolean = false,
    var albums: List<Album> = emptyList(),
    var photos: List<PhotoAsset> = emptyList(),
    private val photosByAlbum: MutableMap<String, List<PhotoAsset>> = mutableMapOf()
) : PhotoRepository {

    /** Counters — let tests assert the VM called the right thing. */
    var loadAlbumsCalls: Int = 0
        private set
    var loadPhotosCalls: MutableList<String?> = mutableListOf()
        private set

    /** Throw from the next `loadPhotos` / `loadAlbums` if set. Cleared after throwing. */
    var photosError: Throwable? = null
    var albumsError: Throwable? = null

    override fun hasReadPermission(): Boolean = initialHasPermission
    override fun hasPartialPhotoAccess(): Boolean = initialHasPartialAccess

    override suspend fun loadAlbums(): List<Album> {
        loadAlbumsCalls++
        albumsError?.let { e ->
            albumsError = null
            throw e
        }
        return albums
    }

    override suspend fun loadPhotos(albumId: String?): List<PhotoAsset> {
        loadPhotosCalls += albumId
        photosError?.let { e ->
            photosError = null
            throw e
        }
        if (albumId != null && albumId != Album.ALL_PHOTOS_ID) {
            photosByAlbum[albumId]?.let { return it }
        }
        return photos
    }

    fun setPhotosForAlbum(albumId: String, items: List<PhotoAsset>) {
        photosByAlbum[albumId] = items
    }
}
