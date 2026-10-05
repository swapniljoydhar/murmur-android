package com.joy.murmur

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.QueueItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PlaybackDatabaseTest {
    @Test
    fun queueAndPositionSurviveDatabaseRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "playback-test-${UUID.randomUUID()}.db"
        val queueItem = QueueItemEntity(
            mediaId = "content://test/audio/1",
            uri = "content://test/audio/1",
            title = "Locally selected audio",
            contentType = "audiobook",
            queueIndex = 0,
        )

        val firstDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        val firstStore = PlaybackStore(firstDatabase)
        firstStore.replaceQueue(listOf(queueItem))
        firstStore.saveState(queueItem.mediaId, 42_000L)
        firstDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        val reopenedStore = PlaybackStore(reopenedDatabase)
        assertEquals(listOf(queueItem), reopenedStore.queue())
        assertEquals(queueItem.mediaId, reopenedStore.state()?.currentMediaId)
        assertEquals(42_000L, reopenedStore.state()?.positionMs)
        reopenedDatabase.close()
        context.deleteDatabase(databaseName)
        Unit
    }
}
