package com.joy.murmur.data.music

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.joy.murmur.data.playback.MusicCatalogStore
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.settings.DataStoreSettingsRepository
import com.joy.murmur.data.settings.settingsDataStore
import com.joy.murmur.domain.music.MusicPermissionRequiredException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class MusicCatalogRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = DataStoreSettingsRepository(applicationContext.settingsDataStore)
        if (!settings.backgroundMusicRefresh.first()) {
            MusicCatalogRefreshScheduler.cancel(applicationContext)
            return Result.success()
        }
        val repository = MediaStoreMusicLibraryRepository(
            applicationContext,
            MusicCatalogStore(PlaybackDatabase.getInstance(applicationContext)),
        )
        return try {
            repository.refresh()
            Result.success()
        } catch (_: MusicPermissionRequiredException) {
            MusicCatalogRefreshScheduler.cancel(applicationContext)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            MusicCatalogRefreshScheduler.cancel(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object MusicCatalogRefreshScheduler {
    private const val UNIQUE_WORK_NAME = "murmur_music_catalog_refresh"

    fun schedule(context: Context) {
        val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<MusicCatalogRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
