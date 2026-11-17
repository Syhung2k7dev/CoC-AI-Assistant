package dev.syhung.cocassistant

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.util.UUID

sealed class VisionObservationResult {
    data class Known(val template: JSONObject, val distance: Int) : VisionObservationResult()
    data class NeedsReview(val candidate: JSONObject, val distance: Int) : VisionObservationResult()
}

/**
 * Local template-learning lifecycle.
 *
 * Novel images become pending candidates. Only an explicit approval promotes a candidate
 * into the verified template collection; model output never silently changes active templates.
 */
class VisionLearningManager(context: Context) {
    private val appContext = context.applicationContext
    private val cadss = CadssStore(appContext)

    @Synchronized
    fun observe(
        bitmap: Bitmap,
        suggestedLabel: String = "",
        metadata: JSONObject = JSONObject(),
        threshold: Int = DEFAULT_MATCH_THRESHOLD
    ): VisionObservationResult {
        require(threshold in 0..16)
        require(suggestedLabel.length <= 120)
        require(metadata.toString().length <= MAX_METADATA_CHARS)
        val hash = VisionProcessor.perceptualHash(bitmap)

        val verified = cadss.getRecords(TEMPLATES)
            .filter { it.optString("status") == STATUS_VERIFIED }
        val known = VisionWatchdog.nearest(hash, verified)
        if (known.record != null && known.distance <= threshold) {
            val updated = JSONObject(known.record.toString())
                .put("observations", known.record.optInt("observations", 0) + 1)
                .put("lastSeenAt", System.currentTimeMillis())
            cadss.putRecord(TEMPLATES, "id", updated.optString("id"), updated)
            return VisionObservationResult.Known(updated, known.distance)
        }

        val pending = cadss.getRecords(CANDIDATES)
            .filter { it.optString("status") == STATUS_PENDING }
        val pendingMatch = VisionWatchdog.nearest(hash, pending)
        if (pendingMatch.record != null && pendingMatch.distance <= threshold) {
            val updated = JSONObject(pendingMatch.record.toString())
                .put("sightings", pendingMatch.record.optInt("sightings", 1) + 1)
                .put("lastSeenAt", System.currentTimeMillis())
            cadss.putRecord(CANDIDATES, "id", updated.optString("id"), updated)
            return VisionObservationResult.NeedsReview(updated, pendingMatch.distance)
        }

        val asset = VisionMemory.save(appContext, bitmap, hash)
        val id = UUID.randomUUID().toString()
        val candidate = JSONObject(asset.toString())
            .put("id", id)
            .put("status", STATUS_PENDING)
            .put("suggestedLabel", suggestedLabel.trim())
            .put("metadata", JSONObject(metadata.toString()))
            .put("sightings", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("lastSeenAt", System.currentTimeMillis())
        cadss.putRecord(CANDIDATES, "id", id, candidate)
        return VisionObservationResult.NeedsReview(candidate, Int.MAX_VALUE)
    }

    /**
     * Imports legacy auto-promoted templates as pending candidates. Legacy templates are never
     * trusted automatically after the approval-gated learning lifecycle is enabled.
     */
    @Synchronized
    fun migrateLegacyTemplates(records: List<JSONObject>): Int {
        var migrated = 0
        records.forEach { legacy ->
            val id = legacy.optString("id").ifBlank { UUID.randomUUID().toString() }
            if (cadss.findRecord(CANDIDATES, "id", id) != null ||
                cadss.findRecord(TEMPLATES, "id", id) != null) return@forEach
            if (VisionMemory.resolve(appContext, legacy.optString("original")) == null ||
                VisionMemory.resolve(appContext, legacy.optString("crop")) == null) return@forEach
            val candidate = JSONObject(legacy.toString())
                .put("id", id)
                .put("status", STATUS_PENDING)
                .put("suggestedLabel", legacy.optString("label"))
                .put("metadata", JSONObject().put("legacyImported", true))
                .put("sightings", 1)
                .put("migratedAt", System.currentTimeMillis())
            cadss.putRecord(CANDIDATES, "id", id, candidate)
            migrated++
        }
        return migrated
    }

    @Synchronized
    fun approve(candidateId: String, confirmedLabel: String): JSONObject {
        require(confirmedLabel.isNotBlank() && confirmedLabel.length <= 120) {
            "Nhãn template phải có từ 1 đến 120 ký tự."
        }
        val candidate = cadss.findRecord(CANDIDATES, "id", candidateId)
            ?: throw IllegalArgumentException("Không tìm thấy Vision candidate.")
        require(candidate.optString("status") == STATUS_PENDING) {
            "Chỉ candidate đang chờ duyệt mới được phát hành."
        }
        require(VisionMemory.resolve(appContext, candidate.optString("original")) != null &&
            VisionMemory.resolve(appContext, candidate.optString("crop")) != null) {
            "Ảnh nguồn của candidate không còn tồn tại."
        }

        val now = System.currentTimeMillis()
        val template = JSONObject(candidate.toString())
            .put("status", STATUS_VERIFIED)
            .put("label", confirmedLabel.trim())
            .put("version", 1)
            .put("successCount", 0)
            .put("failureCount", 0)
            .put("confidence", 0.5)
            .put("approvedAt", now)
            .put("updatedAt", now)
        cadss.putRecord(TEMPLATES, "id", candidateId, template)
        cadss.putRecord(
            CANDIDATES,
            "id",
            candidateId,
            JSONObject(candidate.toString()).put("status", STATUS_PROMOTED).put("promotedAt", now)
        )
        return template
    }

    @Synchronized
    fun reject(candidateId: String, reason: String = ""): Boolean {
        val candidate = cadss.findRecord(CANDIDATES, "id", candidateId) ?: return false
        if (candidate.optString("status") != STATUS_PENDING) return false
        VisionMemory.deleteAssets(appContext, candidate)
        val rejected = JSONObject(candidate.toString())
            .put("status", STATUS_REJECTED)
            .put("original", "")
            .put("crop", "")
            .put("rejectionReason", reason.take(240))
            .put("rejectedAt", System.currentTimeMillis())
        cadss.putRecord(CANDIDATES, "id", candidateId, rejected)
        return true
    }

    @Synchronized
    fun recordOutcome(templateId: String, successful: Boolean): JSONObject? {
        val template = cadss.findRecord(TEMPLATES, "id", templateId) ?: return null
        if (template.optString("status") != STATUS_VERIFIED) return null
        val successes = template.optInt("successCount", 0) + if (successful) 1 else 0
        val failures = template.optInt("failureCount", 0) + if (successful) 0 else 1
        val confidence = (successes + 1.0) / (successes + failures + 2.0)
        val status = if (failures >= 3 && confidence < REVIEW_CONFIDENCE) {
            STATUS_REVIEW_REQUIRED
        } else {
            STATUS_VERIFIED
        }
        val updated = JSONObject(template.toString())
            .put("successCount", successes)
            .put("failureCount", failures)
            .put("confidence", confidence)
            .put("status", status)
            .put("updatedAt", System.currentTimeMillis())
        cadss.putRecord(TEMPLATES, "id", templateId, updated)
        return updated
    }

    fun findNearest(hash: String, threshold: Int = DEFAULT_MATCH_THRESHOLD): Pair<JSONObject, Int>? {
        require(threshold in 0..16)
        require(hash.matches(Regex("[0-9a-fA-F]{16}")))
        val match = VisionWatchdog.nearest(
            hash,
            cadss.getRecords(TEMPLATES).filter { it.optString("status") == STATUS_VERIFIED }
        )
        val record = match.record ?: return null
        return if (match.distance <= threshold) record to match.distance else null
    }

    fun listCandidates(includeResolved: Boolean = false): List<JSONObject> =
        cadss.getRecords(CANDIDATES).filter {
            includeResolved || it.optString("status") == STATUS_PENDING
        }

    fun listTemplates(includeReviewRequired: Boolean = false): List<JSONObject> =
        cadss.getRecords(TEMPLATES).filter {
            it.optString("status") == STATUS_VERIFIED ||
                (includeReviewRequired && it.optString("status") == STATUS_REVIEW_REQUIRED)
        }

    fun resolveImage(template: JSONObject, crop: Boolean = false): File? =
        VisionMemory.resolve(appContext, template.optString(if (crop) "crop" else "original"))

    companion object {
        const val DEFAULT_MATCH_THRESHOLD = 5
        private const val MAX_METADATA_CHARS = 16_000
        private const val REVIEW_CONFIDENCE = 0.4
        private const val CANDIDATES = "vision_candidates"
        private const val TEMPLATES = "vision_templates"
        private const val STATUS_PENDING = "PENDING_REVIEW"
        private const val STATUS_VERIFIED = "VERIFIED"
        private const val STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED"
        private const val STATUS_PROMOTED = "PROMOTED"
        private const val STATUS_REJECTED = "REJECTED"
    }
}
