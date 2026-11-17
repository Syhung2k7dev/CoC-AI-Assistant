package dev.syhung.cocassistant

import android.content.Context
import org.json.JSONObject

/** Compatibility facade over the real per-Workflow WorkManager scheduler. */
object SchedulerManager {
    fun scheduleDaily(
        context: Context,
        workflowId: String,
        version: Int,
        inputs: JSONObject = JSONObject()
    ): String = WorkflowScheduler.schedulePeriodic(context, workflowId, version, 24 * 60L, inputs)

    fun cancel(context: Context, scheduleId: String) {
        WorkflowScheduler.cancel(context, scheduleId)
    }
}
