package com.joy.murmur

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.QueueItemEntity
import com.joy.murmur.data.playback.DocumentStructureEntity
import com.joy.murmur.data.playback.ReaderDocumentEntity
import com.joy.murmur.data.playback.ReaderDocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PlaybackDatabaseTest {
    @Test
    fun changedReaderSourceResetsPositionAndInvalidatesCachedStructure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "reader-source-change-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        val store = ReaderDocumentStore(database)
        store.saveDocuments(
            listOf(
                ReaderDocumentEntity(
                    id = "document-1",
                    uri = "content://test/document/1",
                    title = "Local document",
                    mimeType = "text/plain",
                    addedAtMillis = 1L,
                    positionChars = 128L,
                    positionFingerprint = "source-v1",
                ),
            ),
        )
        store.saveDocumentStructure(
            DocumentStructureEntity(
                documentId = "document-1",
                mimeType = "text/plain",
                totalChars = 512L,
                totalLines = 8L,
                sectionsJson = "[]",
                fingerprint = "index-v1",
                indexVersion = 1,
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            ),
        )

        store.updateReaderSourcePosition("document-1", 0L, "source-v2", 1, invalidateStructure = true)

        assertEquals(0L, store.getDocument("document-1")?.positionChars)
        assertEquals("source-v2", store.getDocument("document-1")?.positionFingerprint)
        assertEquals(null, store.getDocumentStructure("document-1"))
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun readerPositionMigrationPreservesProgressAndAddsAnchorMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "reader-migration-${UUID.randomUUID()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val legacy = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE reader_documents (id TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, mimeType TEXT NOT NULL, addedAtMillis INTEGER NOT NULL, positionChars INTEGER NOT NULL, PRIMARY KEY(id))",
                        )
                        db.execSQL(
                            "INSERT INTO reader_documents VALUES ('doc', 'content://test/doc', 'Book', 'text/plain', 1, 128)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        legacy.writableDatabase
        legacy.close()

        val migrated = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        if (oldVersion == 13 && newVersion == 14) PlaybackDatabase.MIGRATION_13_14.migrate(db)
                    }
                })
                .build(),
        )
        val cursor = migrated.writableDatabase.query(
            "SELECT positionChars, positionFingerprint, positionAnchorVersion FROM reader_documents WHERE id = 'doc'",
        )
        cursor.use {
            assertEquals(true, it.moveToFirst())
            assertEquals(128L, it.getLong(0))
            assertEquals(true, it.isNull(1))
            assertEquals(1, it.getInt(2))
        }
        migrated.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun readerPositionSourceFingerprintSurvivesDatabaseRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "reader-test-${UUID.randomUUID()}.db"
        val document = ReaderDocumentEntity(
            id = "document-1",
            uri = "content://test/document/1",
            title = "Local document",
            mimeType = "text/plain",
            addedAtMillis = 1L,
            positionChars = 128L,
            positionFingerprint = "source-v1",
        )

        val firstDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        ReaderDocumentStore(firstDatabase).saveDocuments(listOf(document))
        firstDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        assertEquals(document, ReaderDocumentStore(reopenedDatabase).getDocument(document.id))
        reopenedDatabase.close()
        context.deleteDatabase(databaseName)
    }

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
