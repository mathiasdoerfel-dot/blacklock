package de.matze.blackbox.tools.mediacleaner

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = CleanerRepository(applicationContext)
        if (!repo.hasAllFilesAccess()) {
            return@withContext Result.success()
        }
        repo.runCleanup(ignoreMinAge = false)
        Result.success()
    }
}

object CleanupScheduler {
    private const val UNIQUE_WORK_NAME = "blackbox_media_cleanup_periodic"

    fun syncSchedule(context: Context) {
        val config = CleanerConfig(context)
        val mode = config.scheduleMode

        if (mode == ScheduleMode.MANUAL || mode.intervalDays <= 0L) {
            // If no automated schedule was ever configured, do not even initialize or touch WorkManager
            if (config.hasConfiguredSchedule) {
                try {
                    val workManager = WorkManager.getInstance(context)
                    workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                    workManager.pruneWork()
                } catch (_: Exception) {}
                config.hasConfiguredSchedule = false
            }
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(config.requireCharging)
            .build()

        val request = PeriodicWorkRequestBuilder<CleanupWorker>(
            mode.intervalDays, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        try {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            config.hasConfiguredSchedule = true
        } catch (_: Exception) {}
    }
}
