package dev.syhung.cocassistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionLearningTest {
    @Test
    fun perceptualHashDistanceCountsChangedBits() {
        assertEquals(0, VisionProcessor.hammingDistance("0000000000000000", "0000000000000000"))
        assertEquals(1, VisionProcessor.hammingDistance("0000000000000000", "0000000000000001"))
        assertEquals(64, VisionProcessor.hammingDistance("0000000000000000", "ffffffffffffffff"))
    }

    @Test
    fun watchdogChoosesNearestTemplateAndAppliesNoveltyThreshold() {
        val records = listOf(
            JSONObject().put("id", "far").put("hash", "00000000000000ff"),
            JSONObject().put("id", "near").put("hash", "0000000000000003")
        )
        val nearest = VisionWatchdog.nearest("0000000000000001", records)
        assertEquals("near", nearest.record?.optString("id"))
        assertEquals(1, nearest.distance)
        assertFalse(VisionWatchdog.learnIfNovel("0000000000000001", records, threshold = 1))
        assertTrue(VisionWatchdog.learnIfNovel("0000000000000001", records, threshold = 0))
    }

    @Test
    fun invalidHashNeverMatchesAndIsConsideredNovel() {
        assertEquals(Int.MAX_VALUE, VisionProcessor.hammingDistance("invalid", "0000000000000000"))
        assertTrue(VisionWatchdog.learnIfNovel("0000000000000000", emptyList()))
    }
}
