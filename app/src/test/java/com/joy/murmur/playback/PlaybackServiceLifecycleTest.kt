package com.joy.murmur.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackServiceLifecycleTest {

    private class StubCarConnectionProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    @Test
    fun `instantiating PlaybackService before attachBaseContext does not throw NPE`() {
        val unattachedService = PlaybackService()
        assertNotNull(unattachedService)
        unattachedService.onDestroy()
    }

    @Test
    fun `PlaybackService creates session with sessionActivity and preserves cached WAV files on destroy`() {
        ShadowBuild.setManufacturer("Google")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = File(context.cacheDir, "read-aloud").apply { mkdirs() }
        val cachedWav = File(cacheDir, "preserved_across_destroy.wav").apply {
            writeBytes(ByteArray(128) { 0x11 })
        }

        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()
        assertNotNull(service)

        service.onStartCommand(
            Intent(PlaybackService.ACTION_START_SLEEP_TIMER).putExtra(PlaybackService.EXTRA_SLEEP_TIMER_MINUTES, 5),
            0,
            1,
        )
        service.onStartCommand(
            Intent(PlaybackService.ACTION_CANCEL_SLEEP_TIMER),
            0,
            2,
        )

        val session = service.currentSessionForTesting()
        assertNotNull("MediaSession must be initialized", session)
        assertNotNull("MediaSession must configure sessionActivity PendingIntent", session!!.sessionActivity)

        // Allow Media3 background observer task to run cleanly without missing-provider failure
        Thread.sleep(50L)

        controller.destroy()
        assertTrue("onDestroy must release pins without wiping cached WAV files", cachedWav.exists())
        cachedWav.delete()
    }

    @Test
    fun `createCarSafeSessionContext blocks missing car provider lookup on Media3 background thread and allows normal callers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val guardedAppContext = createCarSafeSessionContext(context).applicationContext

        // Normal callers on main thread still obtain ContentResolver even when provider is absent
        assertNotNull(guardedAppContext.contentResolver)

        // Simulated Media3 background car-connection observer throws cleanly (caught by Media3) when provider is absent
        var blockedWhenAbsent = false
        val worker = Thread({
            try {
                guardedAppContext.contentResolver
                fail("Expected missing car provider guard to abort ContentResolver lookup on Media3 background thread")
            } catch (_: IllegalStateException) {
                blockedWhenAbsent = true
            }
        }, "ExoPlayer:BackgroundExecutor")
        worker.start()
        worker.join(2_000L)
        assertTrue("Guard must block missing androidx.car.app.connection provider lookup", blockedWhenAbsent)

        // Once Android Auto's provider is installed in PackageManager, ContentResolver is returned normally
        val providerInfo = ProviderInfo().apply {
            authority = CAR_CONNECTION_AUTHORITY
            packageName = "com.google.android.projection.gearhead"
            name = StubCarConnectionProvider::class.java.name
        }
        org.robolectric.Shadows.shadowOf(context.packageManager).addOrUpdateProvider(providerInfo)
        Robolectric.buildContentProvider(StubCarConnectionProvider::class.java).create(providerInfo)

        var resolvedWhenInstalled = false
        val workerInstalled = Thread({
            assertNotNull(guardedAppContext.contentResolver)
            resolvedWhenInstalled = true
        }, "ExoPlayer:BackgroundExecutor")
        workerInstalled.start()
        workerInstalled.join(2_000L)
        assertTrue("Guard must allow ContentResolver access when car connection provider is installed", resolvedWhenInstalled)
    }

    @Test
    fun `isTrustedControllerIdentity rejects untrusted UIDs and accepts own system bluetooth or notification controllers`() {
        val appUid = 10_234
        val untrustedUid = 10_999

        assertFalse(PlaybackService.isTrustedControllerIdentity(untrustedUid, appUid, isNotificationOrAutoCompanion = false))
        assertTrue(PlaybackService.isTrustedControllerIdentity(untrustedUid, appUid, isNotificationOrAutoCompanion = true))
        assertTrue(PlaybackService.isTrustedControllerIdentity(appUid, appUid, isNotificationOrAutoCompanion = false))
        assertTrue(PlaybackService.isTrustedControllerIdentity(Process.SYSTEM_UID, appUid, isNotificationOrAutoCompanion = false))
        assertTrue(PlaybackService.isTrustedControllerIdentity(Process.BLUETOOTH_UID, appUid, isNotificationOrAutoCompanion = false))
    }
}

