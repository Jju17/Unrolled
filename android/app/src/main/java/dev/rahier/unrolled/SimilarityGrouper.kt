package dev.rahier.unrolled

object SimilarityGrouper {
    const val HASH_WIDTH = 9
    const val HASH_HEIGHT = 8
    const val DEFAULT_MAX_GAP_SECONDS = 10
    const val DEFAULT_MAX_RUN_SIZE = 6
    const val DEFAULT_MAX_DISTANCE = 12

    /** Consecutive photos shot within [maxGapSeconds] of the previous one. */
    fun candidateRuns(
        photos: List<PhotoAsset>,
        maxGapSeconds: Int = DEFAULT_MAX_GAP_SECONDS,
        maxRunSize: Int = DEFAULT_MAX_RUN_SIZE
    ): List<List<Long>> {
        val runs = mutableListOf<List<Long>>()
        var current = mutableListOf<Long>()
        var previousTime: Long? = null
        fun flush() {
            if (current.size >= 2) runs += current.toList()
            current = mutableListOf()
        }
        for (photo in photos) {
            val time = photo.creationDate?.time
            if (photo.isVideo || time == null) {
                flush()
                previousTime = null
                continue
            }
            val gap = previousTime?.let { kotlin.math.abs(it - time) }
            if (gap != null && gap <= maxGapSeconds * 1000L && current.size < maxRunSize) {
                current += photo.id
            } else {
                flush()
                current += photo.id
            }
            previousTime = time
        }
        flush()
        return runs
    }

    /** [gray] is a 9x8 row-major grayscale buffer, values 0..255. */
    fun dHash(gray: IntArray): Long {
        require(gray.size == HASH_WIDTH * HASH_HEIGHT) { "expected ${HASH_WIDTH * HASH_HEIGHT} samples" }
        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val left = gray[y * HASH_WIDTH + x]
                val right = gray[y * HASH_WIDTH + x + 1]
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Splits a run into groups of near-identical photos. Empty until every hash is known. */
    fun confirmGroups(
        run: List<Long>,
        hashes: Map<Long, Long>,
        maxDistance: Int = DEFAULT_MAX_DISTANCE
    ): List<List<Long>> {
        if (run.any { it !in hashes }) return emptyList()
        val groups = mutableListOf<List<Long>>()
        var current = mutableListOf<Long>()
        for (id in run) {
            val lead = current.firstOrNull()
            if (lead == null) {
                current += id
                continue
            }
            val distance = hammingDistance(hashes.getValue(lead), hashes.getValue(id))
            if (distance <= maxDistance) {
                current += id
            } else {
                if (current.size >= 2) groups += current.toList()
                current = mutableListOf(id)
            }
        }
        if (current.size >= 2) groups += current.toList()
        return groups
    }
}
