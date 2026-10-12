package dev.rahier.unrolled

import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimilarityGrouperTest {

    private fun photo(id: Long, seconds: Long?, video: Boolean = false) = PhotoAsset(
        id = id,
        uri = "content://$id",
        creationDate = seconds?.let { Date(it * 1000L) },
        isVideo = video
    )

    @Test
    fun `photos within the gap form a run, others break it`() {
        val photos = listOf(
            photo(1, 1000), photo(2, 1005), photo(3, 1009),
            photo(4, 2000),
            photo(5, 3000), photo(6, 3010)
        )
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(5L, 6L)), SimilarityGrouper.candidateRuns(photos))
    }

    @Test
    fun `photos without a date or videos never join a run`() {
        val photos = listOf(photo(1, 1000), photo(2, null), photo(3, 1002), photo(4, 1004, video = true), photo(5, 1006))
        assertTrue(SimilarityGrouper.candidateRuns(photos).isEmpty())
    }

    @Test
    fun `runs are capped at the maximum size`() {
        val photos = (1L..8L).map { photo(it, 1000 + it) }
        assertEquals(listOf((1L..6L).toList(), listOf(7L, 8L)), SimilarityGrouper.candidateRuns(photos))
    }

    @Test
    fun `dHash sets a bit for every left-brighter-than-right pair`() {
        val gradient = IntArray(72) { index -> 255 - (index % 9) * 20 }
        assertEquals(-1L, SimilarityGrouper.dHash(gradient))
        val flat = IntArray(72) { 128 }
        assertEquals(0L, SimilarityGrouper.dHash(flat))
    }

    @Test
    fun `hamming distance counts differing bits`() {
        assertEquals(0, SimilarityGrouper.hammingDistance(0b1011, 0b1011))
        assertEquals(2, SimilarityGrouper.hammingDistance(0b1011, 0b0010))
        assertEquals(64, SimilarityGrouper.hammingDistance(0L, -1L))
    }

    @Test
    fun `confirmGroups splits a run by distance to the group lead`() {
        val hashes = mapOf(1L to 0L, 2L to 0b11L, 3L to -1L, 4L to (-1L xor 0b1L))
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), SimilarityGrouper.confirmGroups(listOf(1, 2, 3, 4), hashes))
    }

    @Test
    fun `confirmGroups drops singletons`() {
        val hashes = mapOf(1L to 0L, 2L to -1L, 3L to 0b1L)
        assertTrue(SimilarityGrouper.confirmGroups(listOf(1, 2, 3), hashes).isEmpty())
    }

    @Test
    fun `confirmGroups waits for every hash`() {
        val hashes = mapOf(1L to 0L, 2L to 0L)
        assertTrue(SimilarityGrouper.confirmGroups(listOf(1, 2, 3), hashes).isEmpty())
    }
}
