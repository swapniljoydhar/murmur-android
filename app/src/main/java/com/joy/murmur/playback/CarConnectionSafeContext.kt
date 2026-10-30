package com.joy.murmur.playback

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.os.Build

internal const val CAR_CONNECTION_AUTHORITY = "androidx.car.app.connection"
private const val MEDIA3_BACKGROUND_THREAD_NAME = "ExoPlayer:BackgroundExecutor"

/**
 * Wraps the [Context] passed to Media3's [androidx.media3.session.MediaSession.Builder] so that
 * `AndroidAutoConnectionStateObserver` skips querying `content://androidx.car.app.connection` when
 * no package on the device provides that authority (such as emulators or devices without Android
 * Auto installed), preventing `E/ActivityThread: Failed to find provider info for androidx.car.app.connection`.
 *
 * When Android Auto is installed (`resolveContentProvider` returns non-null), the underlying
 * [ContentResolver] is returned normally so companion projection state works as expected.
 */
internal fun createCarSafeSessionContext(base: Context): Context = CarSafeSessionContext(base)

private class CarSafeSessionContext(base: Context) : ContextWrapper(base) {
    private val guardedApplicationContext: Context by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CarSafeApplicationContext(base.applicationContext ?: base)
    }

    override fun getApplicationContext(): Context = guardedApplicationContext
}

private class CarSafeApplicationContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this

    override fun getContentResolver(): ContentResolver {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isCarConnectionProviderInstalled(this) &&
            isAndroidAutoConnectionQuery()
        ) {
            throw IllegalStateException("ContentProvider $CAR_CONNECTION_AUTHORITY is not installed")
        }
        return super.getContentResolver()
    }
}

internal fun isCarConnectionProviderInstalled(context: Context): Boolean =
    runCatching {
        context.packageManager.resolveContentProvider(CAR_CONNECTION_AUTHORITY, 0) != null
    }.getOrDefault(false)

internal fun isAndroidAutoConnectionQuery(
    stackTrace: Array<StackTraceElement> = Throwable().stackTrace,
    threadName: String = Thread.currentThread().name,
): Boolean {
    if (stackTrace.any { it.className.endsWith("AndroidAutoConnectionStateObserver") }) {
        return true
    }
    return threadName == MEDIA3_BACKGROUND_THREAD_NAME &&
        stackTrace.none { it.className.contains("DataSource") }
}
