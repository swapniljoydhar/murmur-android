package com.joy.murmur.data.playback

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import android.net.Uri
import com.joy.murmur.playback.PlaybackContentType

@Entity(tableName = "playback_queue")
data class QueueItemEntity(
    @PrimaryKey val mediaId: String,
    val uri: String,
    val title: String,
    val contentType: String,
    val queueIndex: Int,
    val subtitle: String? = null,
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentMediaId: String?,
    val positionMs: Long,
    val isPlaying: Boolean = false,
)

@Entity(tableName = "listening_progress")
data class ListeningProgressEntity(
    @PrimaryKey val mediaId: String,
    val uri: String,
    val title: String,
    val contentType: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMillis: Long,
)

@Entity(tableName = "audiobook_library")
data class AudiobookEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val author: String?,
    val durationMs: Long,
    val coverArt: ByteArray?,
    val addedAtMillis: Long,
)

@Entity(
    tableName = "audiobook_bookmarks",
    foreignKeys = [ForeignKey(
        entity = AudiobookEntity::class,
        parentColumns = ["id"],
        childColumns = ["audiobookId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("audiobookId")],
)
data class AudiobookBookmarkEntity(
    @PrimaryKey val id: String,
    val audiobookId: String,
    val positionMs: Long,
    val label: String,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "audiobook_chapters",
    primaryKeys = ["audiobookId", "chapterIndex"],
    foreignKeys = [ForeignKey(
        entity = AudiobookEntity::class,
        parentColumns = ["id"],
        childColumns = ["audiobookId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("audiobookId")],
)
data class AudiobookChapterEntity(
    val audiobookId: String,
    val chapterIndex: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long?,
)

@Entity(tableName = "processor_settings")
data class ProcessorSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val replayGainEnabled: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val crossfeedEnabled: Boolean = false,
    val headroomEnabled: Boolean = false,
    val balanceEnabled: Boolean = false,
    val ditherEnabled: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class AudiobookWithProgress(
    @Embedded val audiobook: AudiobookEntity,
    @Relation(parentColumn = "id", entityColumn = "mediaId")
    val progress: ListeningProgressEntity?,
)

@Entity(tableName = "music_catalog")
data class MusicTrackEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val relativePath: String?,
    val mimeType: String?,
    val dateModifiedSeconds: Long,
    val syncToken: String,
)

@Entity(tableName = "music_catalog_state")
data class MusicCatalogStateEntity(
    @PrimaryKey val id: Int = 0,
    val hasScanned: Boolean,
)

@Entity(tableName = "music_playlists")
data class MusicPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "music_playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [ForeignKey(
        entity = MusicPlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlistId"), Index(value = ["playlistId", "position"])],
)
data class MusicPlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val position: Int,
)

data class MusicPlaylistWithTracks(
    @Embedded val playlist: MusicPlaylistEntity,
    @Relation(parentColumn = "id", entityColumn = "playlistId")
    val tracks: List<MusicPlaylistTrackEntity>,
)

@Entity(tableName = "reader_documents")
data class ReaderDocumentEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val mimeType: String,
    val addedAtMillis: Long,
    val positionChars: Long,
)

@Entity(
    tableName = "reader_bookmarks",
    foreignKeys = [ForeignKey(
        entity = ReaderDocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("documentId")],
)
data class ReaderBookmarkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val positionChars: Long,
    val label: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "document_structures")
data class DocumentStructureEntity(
    @PrimaryKey val documentId: String,
    val mimeType: String,
    val totalChars: Long,
    val totalLines: Long,
    val sectionsJson: String,
    val fingerprint: String,
    val indexVersion: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)


@Entity(tableName = "batch_jobs")
data class BatchJobEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val status: Int, // JobStatus enum ordinal
    val totalChapters: Int,
    val completedChapters: Int,
    val currentChapter: Int?,
    val outputUri: String?,
    val voiceId: String,
    val voiceSpeed: Float,
    val mergeOutput: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val error: String?,
)

@Entity(
    tableName = "batch_work_units",
    foreignKeys = [ForeignKey(
        entity = BatchJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId"), Index("jobId", "status")],
)
data class BatchWorkUnitEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val chapterIndex: Int,
    val sectionId: String,
    val startChar: Long,
    val endChar: Long,
    val text: String,
    val status: Int, // WorkUnitStatus enum ordinal
    val outputFile: String?,
    val retryCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class BatchJobStatus(val value: Int) {
    PENDING(0),
    RUNNING(1),
    PAUSED(2),
    INTERRUPTED(3),
    COMPLETED(4),
    CANCELLED(5),
    FAILED(6),
    NEEDS_ATTENTION(7),
}

enum class BatchWorkUnitStatus(val value: Int) {
    PENDING(0),
    GENERATING(1),
    COMPLETED(2),
    FAILED(3),
    CANCELLED(4),
}


@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_queue ORDER BY queueIndex")
    suspend fun getQueue(): List<QueueItemEntity>

    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun getState(): PlaybackStateEntity?

    data class CurrentPlaybackInfo(
        val title: String,
        val subtitle: String?,
        val contentType: String,
        val uri: String,
        val isPlaying: Boolean,
    )

    @Query("SELECT q.title AS title, q.subtitle AS subtitle, q.contentType AS contentType, q.uri AS uri, s.isPlaying AS isPlaying FROM playback_queue q JOIN playback_state s ON q.mediaId = s.currentMediaId WHERE s.id = 0")
    suspend fun getCurrentPlayback(): CurrentPlaybackInfo?

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(items: List<QueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: PlaybackStateEntity)

    @Query("SELECT * FROM listening_progress WHERE mediaId = :mediaId")
    suspend fun getListeningProgress(mediaId: String): ListeningProgressEntity?

    @Query("SELECT * FROM listening_progress ORDER BY updatedAtMillis DESC")
    fun observeListeningProgress(): Flow<List<ListeningProgressEntity>>

    @Query("SELECT * FROM listening_progress WHERE contentType = 'audiobook' AND positionMs > 0 AND (durationMs = 0 OR positionMs < durationMs) ORDER BY updatedAtMillis DESC")
    fun observeInProgressAudiobooks(): Flow<List<ListeningProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveListeningProgress(progress: ListeningProgressEntity)

    @Transaction
    @Query("SELECT * FROM audiobook_library ORDER BY addedAtMillis DESC, title COLLATE NOCASE ASC")
    fun observeAudiobooks(): Flow<List<AudiobookWithProgress>>

    @Query("SELECT * FROM audiobook_library WHERE id = :bookId")
    suspend fun getAudiobook(bookId: String): AudiobookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudiobooks(books: List<AudiobookEntity>)

    @Query("DELETE FROM audiobook_library WHERE id = :bookId")
    suspend fun deleteAudiobook(bookId: String)

    @Query("SELECT * FROM audiobook_bookmarks WHERE audiobookId = :audiobookId ORDER BY positionMs ASC")
    fun observeAudiobookBookmarks(audiobookId: String): Flow<List<AudiobookBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobookBookmark(bookmark: AudiobookBookmarkEntity)

    @Query("DELETE FROM audiobook_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteAudiobookBookmark(bookmarkId: String)

    @Query("SELECT * FROM audiobook_chapters WHERE audiobookId = :audiobookId ORDER BY chapterIndex")
    fun observeAudiobookChapters(audiobookId: String): Flow<List<AudiobookChapterEntity>>

    @Query("DELETE FROM audiobook_chapters WHERE audiobookId = :audiobookId")
    suspend fun deleteAudiobookChapters(audiobookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobookChapters(chapters: List<AudiobookChapterEntity>)

    @Query("SELECT * FROM music_catalog ORDER BY title COLLATE NOCASE ASC")
    fun observeMusicCatalog(): Flow<List<MusicTrackEntity>>

    @Query("SELECT * FROM music_catalog_state WHERE id = 0")
    fun observeMusicCatalogState(): Flow<MusicCatalogStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMusicTracks(items: List<MusicTrackEntity>)

    @Query("DELETE FROM music_catalog WHERE syncToken != :syncToken")
    suspend fun deleteMusicTracksExcept(syncToken: String)

    @Query("DELETE FROM music_catalog")
    suspend fun clearMusicCatalog()

    @Query("DELETE FROM music_catalog WHERE id = :trackId")
    suspend fun deleteMusicTrack(trackId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMusicCatalogState(state: MusicCatalogStateEntity)

    @Transaction
    @Query("SELECT * FROM music_playlists ORDER BY createdAtMillis DESC, id")
    fun observeMusicPlaylists(): Flow<List<MusicPlaylistWithTracks>>

    @Query("SELECT * FROM music_playlists WHERE id = :playlistId")
    suspend fun getMusicPlaylist(playlistId: String): MusicPlaylistEntity?

    @Query("SELECT * FROM music_playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getMusicPlaylistTracks(playlistId: String): List<MusicPlaylistTrackEntity>

    @Query("SELECT COUNT(*) FROM music_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun countMusicPlaylistTracks(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMusicPlaylist(playlist: MusicPlaylistEntity)

    @Query("UPDATE music_playlists SET name = :name WHERE id = :playlistId")
    suspend fun renameMusicPlaylist(playlistId: String, name: String)

    @Query("DELETE FROM music_playlists WHERE id = :playlistId")
    suspend fun deleteMusicPlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMusicPlaylistTrack(track: MusicPlaylistTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceMusicPlaylistTracks(tracks: List<MusicPlaylistTrackEntity>)

    @Query("DELETE FROM music_playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteMusicPlaylistTrack(playlistId: String, trackId: String)

    @Query("DELETE FROM music_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearMusicPlaylistTracks(playlistId: String)

    @Query("UPDATE music_playlist_tracks SET position = position - 1 WHERE playlistId = :playlistId AND position > :position")
    suspend fun compactMusicPlaylistPositions(playlistId: String, position: Int)

    @Query("SELECT * FROM reader_documents ORDER BY addedAtMillis DESC, title COLLATE NOCASE ASC")
    fun observeReaderDocuments(): Flow<List<ReaderDocumentEntity>>

    @Query("SELECT * FROM reader_documents WHERE id = :documentId")
    suspend fun getReaderDocument(documentId: String): ReaderDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaderDocuments(documents: List<ReaderDocumentEntity>)

    @Query("UPDATE reader_documents SET positionChars = :positionChars WHERE id = :documentId")
    suspend fun updateReaderPosition(documentId: String, positionChars: Long)

    @Query("DELETE FROM reader_documents WHERE id = :documentId")
    suspend fun deleteReaderDocument(documentId: String)

    @Query("SELECT * FROM reader_bookmarks WHERE documentId = :documentId ORDER BY createdAtMillis DESC")
    fun observeReaderBookmarks(documentId: String): Flow<List<ReaderBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaderBookmark(bookmark: ReaderBookmarkEntity)

    @Query("DELETE FROM reader_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteReaderBookmark(bookmarkId: String)

    // Backup/restore methods
    @Query("SELECT * FROM music_playlists ORDER BY createdAtMillis DESC, id")
    suspend fun getAllPlaylists(): List<MusicPlaylistEntity>

    @Query("SELECT * FROM music_playlist_tracks ORDER BY playlistId, position")
    suspend fun getAllPlaylistTracks(): List<MusicPlaylistTrackEntity>

    @Query("SELECT * FROM reader_documents")
    suspend fun getAllReaderDocuments(): List<ReaderDocumentEntity>

    @Query("SELECT * FROM reader_bookmarks")
    suspend fun getAllReaderBookmarks(): List<ReaderBookmarkEntity>

    @Query("DELETE FROM reader_bookmarks")
    suspend fun clearAllReaderBookmarks()

    @Query("SELECT * FROM audiobook_bookmarks")
    suspend fun getAllAudiobookBookmarks(): List<AudiobookBookmarkEntity>

    @Query("DELETE FROM audiobook_bookmarks")
    suspend fun clearAllAudiobookBookmarks()

    @Query("SELECT * FROM audiobook_chapters")
    suspend fun getAllAudiobookChapters(): List<AudiobookChapterEntity>

    @Query("DELETE FROM audiobook_chapters")
    suspend fun clearAllAudiobookChapters()

    @Query("SELECT * FROM listening_progress")
    suspend fun getAllListeningProgress(): List<ListeningProgressEntity>
    
    // Document structure methods
    @Query("SELECT * FROM document_structures WHERE documentId = :documentId")
    suspend fun getDocumentStructure(documentId: String): DocumentStructureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocumentStructure(entity: DocumentStructureEntity)

    @Query("SELECT * FROM document_structures WHERE documentId = :documentId")
    fun observeDocumentStructure(documentId: String): Flow<DocumentStructureEntity?>
    
    // Batch TTS methods
    @Query("SELECT * FROM batch_jobs ORDER BY createdAtMillis DESC")
    fun observeBatchJobs(): Flow<List<BatchJobEntity>>

    @Query("SELECT * FROM batch_jobs WHERE id = :jobId")
    suspend fun getBatchJob(jobId: String): BatchJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatchJob(job: BatchJobEntity)

    @Query("UPDATE batch_jobs SET status = :status, completedChapters = :completedChapters, currentChapter = :currentChapter, outputUri = :outputUri, updatedAtMillis = :updatedAtMillis, error = :error WHERE id = :jobId")
    suspend fun updateBatchJob(jobId: String, status: Int, completedChapters: Int, currentChapter: Int?, outputUri: String?, updatedAtMillis: Long, error: String?)

    @Query("DELETE FROM batch_jobs WHERE id = :jobId")
    suspend fun deleteBatchJob(jobId: String)

    @Query("SELECT * FROM batch_work_units WHERE jobId = :jobId ORDER BY chapterIndex, startChar")
    suspend fun getBatchWorkUnits(jobId: String): List<BatchWorkUnitEntity>

    @Query("SELECT * FROM batch_work_units WHERE jobId = :jobId AND status = :status")
    suspend fun getBatchWorkUnitsByStatus(jobId: String, status: Int): List<BatchWorkUnitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatchWorkUnits(units: List<BatchWorkUnitEntity>)

    @Query("UPDATE batch_work_units SET status = :status, outputFile = :outputFile, retryCount = :retryCount, updatedAtMillis = :updatedAtMillis WHERE id = :unitId")
    suspend fun updateBatchWorkUnit(unitId: String, status: Int, outputFile: String?, retryCount: Int, updatedAtMillis: Long)

    @Query("DELETE FROM batch_work_units WHERE jobId = :jobId")
    suspend fun deleteBatchWorkUnits(jobId: String)

    // Processor settings
    @Query("SELECT * FROM processor_settings WHERE id = 0")
    suspend fun getProcessorSettings(): ProcessorSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProcessorSettings(settings: ProcessorSettingsEntity)
}

@Database(
    entities = [QueueItemEntity::class, PlaybackStateEntity::class, ListeningProgressEntity::class, AudiobookEntity::class, AudiobookBookmarkEntity::class, AudiobookChapterEntity::class, MusicTrackEntity::class, MusicCatalogStateEntity::class, MusicPlaylistEntity::class, MusicPlaylistTrackEntity::class, ReaderDocumentEntity::class, ReaderBookmarkEntity::class, DocumentStructureEntity::class, BatchJobEntity::class, BatchWorkUnitEntity::class, ProcessorSettingsEntity::class],
    version = 13,
    exportSchema = true,
)
abstract class PlaybackDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao

    companion object {
        @Volatile
        private var instance: PlaybackDatabase? = null

        fun getInstance(context: Context): PlaybackDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PlaybackDatabase::class.java,
                "murmur.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS music_catalog (id TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, artist TEXT, album TEXT, durationMs INTEGER NOT NULL, relativePath TEXT, mimeType TEXT, dateModifiedSeconds INTEGER NOT NULL, syncToken TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS music_catalog_state (id INTEGER NOT NULL, hasScanned INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS music_playlists (id TEXT NOT NULL, name TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS music_playlist_tracks (playlistId TEXT NOT NULL, trackId TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, artist TEXT, album TEXT, durationMs INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, trackId), FOREIGN KEY(playlistId) REFERENCES music_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_music_playlist_tracks_playlistId ON music_playlist_tracks(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_music_playlist_tracks_playlistId_position ON music_playlist_tracks(playlistId, position)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS listening_progress (mediaId TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, contentType TEXT NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(mediaId))")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS audiobook_library (id TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, author TEXT, durationMs INTEGER NOT NULL, addedAtMillis INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_queue ADD COLUMN subtitle TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobook_library ADD COLUMN coverArt BLOB")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reader_documents (id TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, mimeType TEXT NOT NULL, addedAtMillis INTEGER NOT NULL, positionChars INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS reader_bookmarks (id TEXT NOT NULL, documentId TEXT NOT NULL, positionChars INTEGER NOT NULL, label TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(documentId) REFERENCES reader_documents(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reader_bookmarks_documentId ON reader_bookmarks(documentId)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS audiobook_bookmarks (id TEXT NOT NULL, audiobookId TEXT NOT NULL, positionMs INTEGER NOT NULL, label TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(audiobookId) REFERENCES audiobook_library(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_bookmarks_audiobookId ON audiobook_bookmarks(audiobookId)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS audiobook_chapters (audiobookId TEXT NOT NULL, chapterIndex INTEGER NOT NULL, title TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER, PRIMARY KEY(audiobookId, chapterIndex), FOREIGN KEY(audiobookId) REFERENCES audiobook_library(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_chapters_audiobookId ON audiobook_chapters(audiobookId)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS document_structures (documentId TEXT NOT NULL, mimeType TEXT NOT NULL, totalChars INTEGER NOT NULL, totalLines INTEGER NOT NULL, sectionsJson TEXT NOT NULL, fingerprint TEXT NOT NULL, indexVersion INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(documentId))")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS batch_jobs (id TEXT NOT NULL, documentId TEXT NOT NULL, status INTEGER NOT NULL, totalChapters INTEGER NOT NULL, completedChapters INTEGER NOT NULL, currentChapter INTEGER, outputUri TEXT, voiceId TEXT NOT NULL, voiceSpeed REAL NOT NULL, mergeOutput INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, error TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS batch_work_units (id TEXT NOT NULL, jobId TEXT NOT NULL, chapterIndex INTEGER NOT NULL, sectionId TEXT NOT NULL, startChar INTEGER NOT NULL, endChar INTEGER NOT NULL, text TEXT NOT NULL, status INTEGER NOT NULL, outputFile TEXT, retryCount INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(jobId) REFERENCES batch_jobs(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_batch_work_units_jobId ON batch_work_units(jobId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_batch_work_units_jobId_status ON batch_work_units(jobId, status)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS processor_settings (id INTEGER NOT NULL, replayGainEnabled INTEGER NOT NULL DEFAULT 0, equalizerEnabled INTEGER NOT NULL DEFAULT 0, crossfeedEnabled INTEGER NOT NULL DEFAULT 0, headroomEnabled INTEGER NOT NULL DEFAULT 0, balanceEnabled INTEGER NOT NULL DEFAULT 0, ditherEnabled INTEGER NOT NULL DEFAULT 0, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}

class PlaybackStore(private val database: PlaybackDatabase) {
    private val dao = database.playbackDao()

    suspend fun queue(): List<QueueItemEntity> = dao.getQueue()

    suspend fun state(): PlaybackStateEntity? = dao.getState()

    suspend fun getCurrentTitle(): String? = dao.getCurrentPlayback()?.title

    suspend fun getCurrentSubtitle(): String? = dao.getCurrentPlayback()?.subtitle

    suspend fun isPlaying(): Boolean = dao.getCurrentPlayback()?.isPlaying ?: false

    suspend fun getCurrentArtworkUri(): Uri? =
        dao.getCurrentPlayback()?.uri?.let { Uri.parse(it) }

    suspend fun getCurrentContentType(): PlaybackContentType =
        dao.getCurrentPlayback()?.let {
            PlaybackContentType.valueOf(it.contentType.uppercase())
        } ?: PlaybackContentType.MUSIC

    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        database.withTransaction {
            dao.clearQueue()
            dao.insertQueue(items)
        }
    }

    suspend fun saveState(mediaId: String?, positionMs: Long) {
        dao.saveState(PlaybackStateEntity(currentMediaId = mediaId, positionMs = positionMs.coerceAtLeast(0L)))
    }

    suspend fun getProgress(mediaId: String): ListeningProgressEntity? = dao.getListeningProgress(mediaId)

    fun observeProgress(): Flow<List<ListeningProgressEntity>> = dao.observeListeningProgress()

    fun observeAudiobookProgress(): Flow<List<ListeningProgressEntity>> = dao.observeInProgressAudiobooks()

    suspend fun getProcessorSettings(): ProcessorSettingsEntity? = dao.getProcessorSettings()

    suspend fun persistSnapshot(queue: List<QueueItemEntity>, mediaId: String?, positionMs: Long, durationMs: Long) {
        database.withTransaction {
            dao.clearQueue()
            dao.insertQueue(queue)
            dao.saveState(PlaybackStateEntity(currentMediaId = mediaId, positionMs = positionMs.coerceAtLeast(0L)))
            val current = queue.firstOrNull { it.mediaId == mediaId }
            if (current != null) {
                val position = positionMs.coerceAtLeast(0L)
                dao.saveListeningProgress(
                    ListeningProgressEntity(
                        mediaId = current.mediaId,
                        uri = current.uri,
                        title = current.title,
                        contentType = current.contentType,
                        positionMs = if (durationMs > 0L) position.coerceAtMost(durationMs) else position,
                        durationMs = durationMs.coerceAtLeast(0L),
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

class AudiobookStore(private val database: PlaybackDatabase) {
    private val dao = database.playbackDao()

    fun observeBooks(): Flow<List<AudiobookWithProgress>> = dao.observeAudiobooks()

    suspend fun getBook(bookId: String): AudiobookEntity? = dao.getAudiobook(bookId)

    suspend fun saveBooks(books: List<AudiobookEntity>) {
        if (books.isNotEmpty()) dao.upsertAudiobooks(books)
    }

    suspend fun removeBook(bookId: String) = dao.deleteAudiobook(bookId)

    fun observeBookmarks(audiobookId: String): Flow<List<AudiobookBookmarkEntity>> = dao.observeAudiobookBookmarks(audiobookId)

    suspend fun saveBookmark(bookmark: AudiobookBookmarkEntity) = dao.insertAudiobookBookmark(bookmark)

    suspend fun removeBookmark(bookmarkId: String) = dao.deleteAudiobookBookmark(bookmarkId)

    fun observeChapters(audiobookId: String): Flow<List<AudiobookChapterEntity>> = dao.observeAudiobookChapters(audiobookId)

    suspend fun replaceChapters(audiobookId: String, chapters: List<AudiobookChapterEntity>) {
        database.withTransaction {
            dao.deleteAudiobookChapters(audiobookId)
            if (chapters.isNotEmpty()) dao.insertAudiobookChapters(chapters)
        }
    }
}

class MusicCatalogStore(private val database: PlaybackDatabase) {
    private val dao = database.playbackDao()

    fun observeTracks(): Flow<List<MusicTrackEntity>> = dao.observeMusicCatalog()

    fun observeState(): Flow<MusicCatalogStateEntity?> = dao.observeMusicCatalogState()

    suspend fun replaceSnapshot(items: List<MusicTrackEntity>, syncToken: String) {
        database.withTransaction {
            if (items.isEmpty()) {
                dao.clearMusicCatalog()
            } else {
                dao.upsertMusicTracks(items)
                dao.deleteMusicTracksExcept(syncToken)
            }
            dao.saveMusicCatalogState(MusicCatalogStateEntity(hasScanned = true))
        }
    }

    suspend fun upsertChangedTrack(item: MusicTrackEntity) = database.withTransaction {
        dao.upsertMusicTracks(listOf(item))
    }

    suspend fun removeChangedTrack(trackId: String) = database.withTransaction {
        dao.deleteMusicTrack(trackId)
    }
}

class MusicPlaylistStore(private val database: PlaybackDatabase) {
    private val dao = database.playbackDao()

    fun observePlaylists(): Flow<List<MusicPlaylistWithTracks>> = dao.observeMusicPlaylists()

    suspend fun create(playlist: MusicPlaylistEntity) = dao.insertMusicPlaylist(playlist)

    suspend fun rename(playlistId: String, name: String) {
        if (dao.getMusicPlaylist(playlistId) == null) throw NoSuchElementException("Music playlist not found")
        dao.renameMusicPlaylist(playlistId, name)
    }

    suspend fun delete(playlistId: String) = dao.deleteMusicPlaylist(playlistId)

    suspend fun addTrack(playlistId: String, track: MusicPlaylistTrackEntity) {
        database.withTransaction {
            requirePlaylist(playlistId)
            val position = dao.countMusicPlaylistTracks(playlistId)
            dao.insertMusicPlaylistTrack(track.copy(position = position))
        }
    }

    suspend fun removeTrack(playlistId: String, trackId: String) {
        database.withTransaction {
            requirePlaylist(playlistId)
            val tracks = dao.getMusicPlaylistTracks(playlistId)
            val removedPosition = tracks.indexOfFirst { it.trackId == trackId }
            if (removedPosition >= 0) {
                dao.deleteMusicPlaylistTrack(playlistId, trackId)
                dao.compactMusicPlaylistPositions(playlistId, removedPosition)
            }
        }
    }

    suspend fun reorderTracks(playlistId: String, trackIds: List<String>) {
        database.withTransaction {
            requirePlaylist(playlistId)
            val existing = dao.getMusicPlaylistTracks(playlistId)
            require(trackIds.size == existing.size && trackIds.toSet() == existing.map { it.trackId }.toSet()) {
                "Reordered track ids must contain every playlist track exactly once"
            }
            val tracksById = existing.associateBy(MusicPlaylistTrackEntity::trackId)
            dao.clearMusicPlaylistTracks(playlistId)
            dao.replaceMusicPlaylistTracks(trackIds.mapIndexed { index, id -> tracksById.getValue(id).copy(position = index) })
        }
    }

    private suspend fun requirePlaylist(playlistId: String) {
        if (dao.getMusicPlaylist(playlistId) == null) throw NoSuchElementException("Music playlist not found")
    }
}

class ReaderDocumentStore(private val database: PlaybackDatabase) {
    private val dao = database.playbackDao()

    fun observeDocuments(): Flow<List<ReaderDocumentEntity>> = dao.observeReaderDocuments()
    fun observeBookmarks(documentId: String): Flow<List<ReaderBookmarkEntity>> = dao.observeReaderBookmarks(documentId)
    suspend fun saveDocuments(documents: List<ReaderDocumentEntity>) = dao.upsertReaderDocuments(documents)
    suspend fun getDocument(documentId: String): ReaderDocumentEntity? = dao.getReaderDocument(documentId)
    suspend fun savePosition(documentId: String, positionChars: Long) = dao.updateReaderPosition(documentId, positionChars)
    suspend fun removeDocument(documentId: String) = dao.deleteReaderDocument(documentId)
    suspend fun saveBookmark(bookmark: ReaderBookmarkEntity) = dao.insertReaderBookmark(bookmark)
    suspend fun removeBookmark(bookmarkId: String) = dao.deleteReaderBookmark(bookmarkId)

    // Document structure methods
    suspend fun getDocumentStructure(documentId: String): DocumentStructureEntity? = dao.getDocumentStructure(documentId)
    suspend fun saveDocumentStructure(entity: DocumentStructureEntity) = dao.insertDocumentStructure(entity)
    fun observeDocumentStructure(documentId: String): Flow<DocumentStructureEntity?> = dao.observeDocumentStructure(documentId)
}












