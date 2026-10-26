package com.joy.murmur.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joy.murmur.data.playback.MusicPlaylistEntity
import com.joy.murmur.data.playback.MusicPlaylistTrackEntity
import com.joy.murmur.data.playback.PlaybackDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class StorageCenterBackupTest {
    @Test
    fun backupPreviewAndRestorePreservePlaylistAndTrack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-round-trip-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, PlaybackDatabase::class.java, name).build()
        try {
            val dao = database.playbackDao()
            dao.insertMusicPlaylist(MusicPlaylistEntity("playlist", "Road trip", 10L))
            dao.insertMusicPlaylistTrack(MusicPlaylistTrackEntity(
                playlistId = "playlist", trackId = "track", uri = "content://music/1", title = "Song",
                artist = null, album = null, durationMs = 90_000L, position = 0,
            ))
            val storage = StorageCenter(context, database)
            val backup = storage.createBackupBytes()
            val preview = storage.inspectBackupBytes(backup)
            assertEquals(2, preview.recordsByTable.getValue("music_playlists") + preview.recordsByTable.getValue("music_playlist_tracks"))

            dao.deleteMusicPlaylist("playlist")
            dao.insertMusicPlaylist(MusicPlaylistEntity("replacement", "Temporary", 20L))
            storage.restoreBackupBytes(backup)

            assertNotNull(dao.getMusicPlaylist("playlist"))
            assertNull(dao.getMusicPlaylist("replacement"))
            assertEquals("track", dao.getMusicPlaylistTracks("playlist").single().trackId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun duplicatePrimaryKeysAbortRestoreAndPreserveExistingRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-rollback-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, PlaybackDatabase::class.java, name).build()
        try {
            val dao = database.playbackDao()
            dao.insertMusicPlaylist(MusicPlaylistEntity("source", "Source", 1L))
            val storage = StorageCenter(context, database)
            val original = MurmurBackupFormat.decode(storage.createBackupBytes(), expectedDatabaseVersion = 15).content
            val playlistTable = original.tables.getValue("music_playlists")
            val invalidContent = original.copy(tables = original.tables + ("music_playlists" to playlistTable.copy(rows = playlistTable.rows + playlistTable.rows)))
            val invalidBackup = MurmurBackupFormat.encode(invalidContent)

            dao.deleteMusicPlaylist("source")
            dao.insertMusicPlaylist(MusicPlaylistEntity("keep", "Keep me", 2L))
            runCatching { storage.restoreBackupBytes(invalidBackup) }
                .onSuccess { error("Duplicate primary keys must abort the restore.") }

            assertNotNull(dao.getMusicPlaylist("keep"))
            assertNull(dao.getMusicPlaylist("source"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
