package dev.rahier.unrolled

/**
 * In-memory [PhotoRepository] for unit tests: default lists, per-pile
 * mappings, hash maps and throw-on-demand.
 */
class FakePhotoRepository(
    var initialHasPermission: Boolean = true,
    var initialHasPartialAccess: Boolean = false,
    var piles: List<Pile> = emptyList(),
    var photos: List<PhotoAsset> = emptyList(),
    var hashes: Map<Long, Long> = emptyMap(),
    private val photosByPile: MutableMap<PileSelector, List<PhotoAsset>> = mutableMapOf()
) : PhotoRepository {

    var loadPilesCalls: Int = 0
        private set
    var loadPhotosCalls: MutableList<PileSelector> = mutableListOf()
        private set
    var loadPhotosIncludeVideos: MutableList<Boolean> = mutableListOf()
        private set
    var loadPhotosByIdsCalls: MutableList<List<Long>> = mutableListOf()
        private set
    var loadHashesCalls: MutableList<List<Long>> = mutableListOf()
        private set

    /** Throw from the next load if set. Cleared after throwing. */
    var photosError: Throwable? = null
    var pilesError: Throwable? = null

    override fun hasReadPermission(): Boolean = initialHasPermission
    override fun hasPartialPhotoAccess(): Boolean = initialHasPartialAccess

    override suspend fun loadPiles(nowMillis: Long, classifiedIds: Set<Long>, includeVideos: Boolean): List<Pile> {
        loadPilesCalls++
        pilesError?.let { e ->
            pilesError = null
            throw e
        }
        return piles
    }

    override suspend fun loadPhotos(selector: PileSelector, includeVideos: Boolean): List<PhotoAsset> {
        loadPhotosCalls += selector
        loadPhotosIncludeVideos += includeVideos
        photosError?.let { e ->
            photosError = null
            throw e
        }
        photosByPile[selector]?.let { return it }
        val base = if (includeVideos) photos else photos.filterNot { it.isVideo }
        return base
    }

    fun setPhotosForPile(selector: PileSelector, items: List<PhotoAsset>) {
        photosByPile[selector] = items
    }

    override suspend fun loadPhotosByIds(ids: List<Long>): List<PhotoAsset> {
        loadPhotosByIdsCalls += ids
        val known = (photos + photosByPile.values.flatten()).associateBy { it.id }
        return ids.mapNotNull { known[it] }
    }

    override suspend fun loadSimilarityHashes(ids: List<Long>): Map<Long, Long> {
        loadHashesCalls += ids
        return ids.mapNotNull { id -> hashes[id]?.let { id to it } }.toMap()
    }

    override suspend fun loadLocation(photoId: Long): GeoPoint? = null
}
