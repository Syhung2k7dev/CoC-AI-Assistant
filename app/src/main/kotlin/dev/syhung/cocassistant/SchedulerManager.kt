package dev.syhung.cocassistant

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.content.Context as AndroidContext
import java.util.concurrent.TimeUnit

class MaintenanceWorker(context: AndroidContext, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Lightweight maintenance hook; no foreground loop or external-app interaction.
        return Result.success()
    }
}

object SchedulerManager {
    private const val WORK_NAME = "cadss_daily_maintenance"
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
}
