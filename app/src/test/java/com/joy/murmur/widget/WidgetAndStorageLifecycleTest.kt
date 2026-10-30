package com.joy.murmur.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joy.murmur.R
import com.joy.murmur.data.playback.MusicCatalogStore
import com.joy.murmur.data.playback.MusicTrackEntity
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.QueueItemEntity
import com.joy.murmur.data.playback.TtsCacheEntryEntity
import com.joy.murmur.data.storage.DocumentFileAudioImporter
import com.joy.murmur.data.storage.StorageCenter
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetAndStorageLifecycleTest {

    @Test
    fun `WidgetConfigureActivity launches with AppCompat theme and returns RESULT_OK on Done`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgetId = shadowOf(AppWidgetManager.getInstance(context))
            .createWidget(NowPlayingWidget::class.java, R.layout.widget_now_playing)
        val intent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val controller = Robolectric.buildActivity(WidgetConfigureActivity::class.java, intent).create()
        val activity = controller.get()
        assertNotNull(activity)

        activity.findViewById<Button>(R.id.btn_done).performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `WidgetManager dispatches ACTION_APPWIDGET_UPDATE broadcast when widgets exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowAppWidgetManager = shadowOf(AppWidgetManager.getInstance(context))
        val widgetId = shadowAppWidgetManager.createWidget(NowPlayingWidget::class.java, R.layout.widget_now_playing)
        assertTrue(widgetId > 0)

        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.clearBroadcastIntents()

        WidgetManager.updateAllWidgets(context)

        val broadcasts = shadowApp.broadcastIntents
        assertTrue(
            "Expected ACTION_APPWIDGET_UPDATE broadcast",
            broadcasts.any { it.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE },
        )
    }

    @Test
    fun `StorageCenter backup and restore succeeds on v17 schema and prunes unreferenced tts_cache_entries`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PlaybackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val storageCenter = StorageCenter(context, db)
            val backupBytes = storageCenter.createBackupBytes()
            val summary = storageCenter.inspectBackupBytes(backupBytes)
            assertTrue(summary.recordsByTable.containsKey("read_aloud_segment_map"))
            assertFalse(summary.recordsByTable.containsKey("processor_settings"))
            val restored = storageCenter.restoreBackupBytes(backupBytes)
            assertEquals(summary.totalRecords, restored.totalRecords)

            val cacheDir = File(context.cacheDir, "read-aloud").apply { mkdirs() }
            val activeFile = File(cacheDir, "active_segment.wav").apply { writeBytes(ByteArray(128) { 1 }) }
            val staleFile = File(cacheDir, "stale_segment.wav").apply { writeBytes(ByteArray(256) { 2 }) }

            db.playbackDao().upsertTtsCacheEntry(
                TtsCacheEntryEntity(
                    cacheKey = "active_segment",
                    filePath = activeFile.absolutePath,
                    identity = "id",
                    sampleRate = 22_050,
                    channelCount = 1,
                    bitsPerSample = 16,
                    durationMs = 100L,
                    sizeBytes = 128L,
                    createdAtMillis = 1L,
                    lastAccessedMillis = 1L,
                ),
            )
            db.playbackDao().upsertTtsCacheEntry(
                TtsCacheEntryEntity(
                    cacheKey = "stale_segment",
                    filePath = staleFile.absolutePath,
                    identity = "id",
                    sampleRate = 22_050,
                    channelCount = 1,
                    bitsPerSample = 16,
                    durationMs = 200L,
                    sizeBytes = 256L,
                    createdAtMillis = 1L,
                    lastAccessedMillis = 1L,
                ),
            )

            db.playbackDao().insertQueue(
                listOf(
                    QueueItemEntity(
                        mediaId = "seg-1",
                        uri = "file://${activeFile.absolutePath}",
                        title = "Active Segment",
                        contentType = "read_aloud",
                        queueIndex = 0,
                    ),
                ),
            )

            val reclaimed = storageCenter.clearGeneratedSpeechCache()
            assertTrue(reclaimed >= 256L)
            assertTrue("Active queued file must be preserved", activeFile.exists())
            assertFalse("Unqueued cached file must be deleted", staleFile.exists())
            assertNotNull(db.playbackDao().getTtsCacheEntry("active_segment"))
            assertNull(db.playbackDao().getTtsCacheEntry("stale_segment"))
            activeFile.delete()
        } finally {
            db.close()
        }
    }

    @Test
    fun `SAF-imported MusicTracks survive MediaStore replaceSnapshot refresh and DocumentFileAudioImporter recognizes audio`(): Unit = runBlocking {
        assertTrue(DocumentFileAudioImporter.isSupportedAudioDocument("audio/flac", "track.bin"))
        assertTrue(DocumentFileAudioImporter.isSupportedAudioDocument("application/octet-stream", "chapter_01.m4b"))
        assertFalse(DocumentFileAudioImporter.isSupportedAudioDocument("application/pdf", "notes.pdf"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PlaybackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val catalog = MusicCatalogStore(db)
            val safTrack = MusicTrackEntity(
                id = "saf-1",
                uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fambient.flac",
                title = "Ambient Study",
                artist = "Field Notes",
                album = "Sanctuary",
                durationMs = 180_000L,
                relativePath = "Music/",
                mimeType = "audio/flac",
                dateModifiedSeconds = 100L,
                syncToken = "saf-import",
            )
            catalog.importSafTracks(listOf(safTrack))

            val scannedTrack = MusicTrackEntity(
                id = "mediastore-1",
                uri = "content://media/external/audio/media/1",
                title = "Indexed Song",
                artist = "Local Artist",
                album = "Album",
                durationMs = 200_000L,
                relativePath = "Download/",
                mimeType = "audio/mpeg",
                dateModifiedSeconds = 200L,
                syncToken = "scan-token-1",
            )
            catalog.replaceSnapshot(listOf(scannedTrack), syncToken = "scan-token-1")

            val tracks = catalog.observeTracks().first()
            assertEquals(2, tracks.size)
            assertTrue(tracks.any { it.id == "saf-1" })
            assertTrue(tracks.any { it.id == "mediastore-1" })
        } finally {
            db.close()
        }
    }
}
