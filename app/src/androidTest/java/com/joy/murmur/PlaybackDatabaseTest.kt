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
import com.joy.murmur.data.playback.AudiobookEntity
import com.joy.murmur.data.playback.AudiobookChapterEntity
import com.joy.murmur.data.playback.AudiobookStore
import com.joy.murmur.data.playback.ReaderDocumentEntity
import com.joy.murmur.data.playback.ReaderDocumentStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PlaybackDatabaseTest {
    @Test
    fun audiobookChapterSourceSurvivesDatabaseRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "audiobook-chapter-source-${UUID.randomUUID()}.db"
        val bookId = "book-1"
        val firstDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        val firstStore = AudiobookStore(firstDatabase)
        firstStore.saveBooks(
            listOf(AudiobookEntity(bookId, "content://test/book", "Book", null, 60_000L, null, 1L)),
        )
        firstStore.replaceChapters(
            bookId,
            listOf(AudiobookChapterEntity(bookId, 0, "Chapter 1", 0L, 60_000L, "ID3")),
        )
        firstDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        assertEquals("ID3", AudiobookStore(reopenedDatabase).observeChapters(bookId).first().single().source)
        reopenedDatabase.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun audiobookProgressRemainsIndependentAcrossBooksAndDatabaseRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "audiobook-progress-${UUID.randomUUID()}.db"
        val longDurationMs = 237L * 60L * 60L * 1_000L
        val firstBook = QueueItemEntity(
            mediaId = "book-a",
            uri = "content://test/audiobook/a",
            title = "Book A",
            contentType = "audiobook",
            queueIndex = 0,
        )
        val secondBook = QueueItemEntity(
            mediaId = "book-b",
            uri = "content://test/audiobook/b",
            title = "Book B",
            contentType = "audiobook",
            queueIndex = 1,
        )

        val firstDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        val firstStore = PlaybackStore(firstDatabase)
        firstStore.persistSnapshot(listOf(firstBook, secondBook), firstBook.mediaId, longDurationMs - 5_000L, longDurationMs)
        firstStore.persistSnapshot(listOf(firstBook, secondBook), secondBook.mediaId, 45_000L, 90_000L)
        firstDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(context, PlaybackDatabase::class.java, databaseName).build()
        val reopenedStore = PlaybackStore(reopenedDatabase)
        assertEquals(longDurationMs - 5_000L, reopenedStore.getProgress(firstBook.mediaId)?.positionMs)
        assertEquals(longDurationMs, reopenedStore.getProgress(firstBook.mediaId)?.durationMs)
        assertEquals(45_000L, reopenedStore.getProgress(secondBook.mediaId)?.positionMs)
        reopenedDatabase.close()
        context.deleteDatabase(databaseName)
    }

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
                        db.execSQL(
                            "CREATE TABLE audiobook_chapters (audiobookId TEXT NOT NULL, chapterIndex INTEGER NOT NULL, title TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER, PRIMARY KEY(audiobookId, chapterIndex))",
                        )
                        db.execSQL(
                            "INSERT INTO audiobook_chapters VALUES ('book', 0, 'Chapter 1', 0, 60000)",
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
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        if (oldVersion == 13 && newVersion == 15) {
                            PlaybackDatabase.MIGRATION_13_14.migrate(db)
                            PlaybackDatabase.MIGRATION_14_15.migrate(db)
                        }
                    }
                })
                .build(),
        )
        val database = migrated.writableDatabase
        val cursor = database.query(
            "SELECT positionChars, positionFingerprint, positionAnchorVersion FROM reader_documents WHERE id = 'doc'",
        )
        cursor.use {
            assertEquals(true, it.moveToFirst())
            assertEquals(128L, it.getLong(0))
            assertEquals(true, it.isNull(1))
            assertEquals(1, it.getInt(2))
        }
        database.query("SELECT source FROM audiobook_chapters WHERE audiobookId = 'book'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("UNKNOWN", it.getString(0))
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
