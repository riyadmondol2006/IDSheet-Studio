package io.github.riyadmondol2006.idsheetstudio.data.update

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.riyadmondol2006.idsheetstudio.BuildConfig
import io.github.riyadmondol2006.idsheetstudio.appContainer
import java.util.concurrent.TimeUnit

/** Periodically checks GitHub Releases in the background and notifies about new versions. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val settings = container.settings.current()
        if (!settings.autoUpdateCheck) return Result.success()
        val info = try {
            container.updateChecker.findUpdate(BuildConfig.VERSION_NAME.removeSuffix("-debug"), Build.VERSION.SDK_INT)
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
        container.settings.setLastUpdateCheck(System.currentTimeMillis())
        if (info != null && UpdatePolicy.shouldNotify(info, settings)) {
            if (UpdateNotifier.notify(applicationContext, info)) {
                container.settings.setLastNotifiedVersion(info.version.toString())
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "github-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(UpdatePolicy.BACKGROUND_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
