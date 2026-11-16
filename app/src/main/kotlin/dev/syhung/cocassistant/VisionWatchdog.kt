package dev.syhung.cocassistant

import org.json.JSONObject

/** Theo dõi độ tương đồng và học mẫu có kiểm soát dựa trên mã pHash. */
object VisionWatchdog {
    data class Match(val record: JSONObject?, val distance: Int)

    fun nearest(hash: String, records: List<JSONObject>): Match {
        var best: JSONObject? = null
        var bestDistance = Int.MAX_VALUE
        records.forEach { record ->
            val distance = VisionProcessor.hammingDistance(hash, record.optString("hash"))
            if (distance < bestDistance) { bestDistance = distance; best = record }
        }
        return Match(best, bestDistance)
    }

    fun learnIfNovel(hash: String, records: List<JSONObject>, threshold: Int = 5): Boolean {
        require(threshold in 0..16)
        val match = nearest(hash, records)
        return match.record == null || match.distance > threshold
    }
}
