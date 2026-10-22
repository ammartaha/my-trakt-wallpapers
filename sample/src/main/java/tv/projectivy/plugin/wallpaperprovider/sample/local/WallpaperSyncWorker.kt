package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager
import tv.projectivy.plugin.wallpaperprovider.sample.WallpaperProvider
import java.util.concurrent.TimeUnit
import java.io.IOException

class WallpaperSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        PreferencesManager.init(applicationContext)
        val cache = WallpaperCache(applicationContext)
        val before = cache.publicationState()
        return try {
            LocalWallpaperGenerator(applicationContext).syncAndGenerate(inputData.getBoolean("force", false))
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PreferencesManager.syncStatus = when (error) {
                is ApiFailure -> error.message.orEmpty()
                is IOException -> "Network or login check failed. Saved wallpapers remain available. Try Refresh now, or Connect Trakt."
                else -> "The list check did not finish. Saved wallpapers were kept. Try Refresh now."
            }
            if (error is ApiFailure && error.status in setOf(401, 403, 404, 420, 423, 426)) Result.failure()
            else if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (error: OutOfMemoryError) {
            PreferencesManager.syncStatus = "Not enough free memory for 4K rendering. Close other apps, then try Refresh now."
            Result.failure()
        } finally {
            // A later failed download must not hide a wallpaper already published in this run.
            if (!isStopped && before != cache.publicationState()) WallpaperProvider.forceRefresh(applicationContext)
        }
    }
}

object WallpaperWork {
    const val PERIODIC = "my-trakt-periodic-sync"
    const val IMMEDIATE = "my-trakt-refresh"
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val minutes = PreferencesManager.syncMinutes.toLong()
        val request = PeriodicWorkRequestBuilder<WallpaperSyncWorker>(minutes, TimeUnit.MINUTES)
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun requestNow(context: Context, force: Boolean = false) {
        if (SourceSelection.selected().isEmpty()) return
        val request = OneTimeWorkRequestBuilder<WallpaperSyncWorker>()
            .setInputData(workDataOf("force" to force))
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
