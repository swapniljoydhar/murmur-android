package com.joy.murmur.data.music

import android.net.Uri
import com.joy.murmur.data.playback.MusicPlaylistEntity
import com.joy.murmur.data.playback.MusicPlaylistStore
import com.joy.murmur.data.playback.MusicPlaylistTrackEntity
import com.joy.murmur.data.playback.MusicPlaylistWithTracks
import com.joy.murmur.domain.music.MusicPlaylist
import com.joy.murmur.domain.music.MusicPlaylistRepository
import com.joy.murmur.domain.music.MusicPlaylistTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomMusicPlaylistRepository(private val store: MusicPlaylistStore) : MusicPlaylistRepository {
    override fun observePlaylists(): Flow<List<MusicPlaylist>> = store.observePlaylists()
        .map { playlists -> playlists.map(MusicPlaylistWithTracks::toDomain) }
        .flowOn(Dispatchers.Default)

    override suspend fun createPlaylist(name: String): MusicPlaylist {
        val normalizedName = name.normalizePlaylistName()
        val playlist = MusicPlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            createdAtMillis = System.currentTimeMillis(),
        )
        store.create(playlist)
        return MusicPlaylist(playlist.id, playlist.name, playlist.createdAtMillis, emptyList())
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        store.rename(playlistId, name.normalizePlaylistName())
    }

    override suspend fun deletePlaylist(playlistId: String) {
        store.delete(playlistId)
    }

    override suspend fun addTrack(playlistId: String, track: MusicPlaylistTrack) {
        store.addTrack(
            playlistId,
            MusicPlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.trackId,
                uri = track.uri.toString(),
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs.coerceAtLeast(0L),
                position = 0,
            ),
        )
    }

    override suspend fun removeTrack(playlistId: String, trackId: String) {
        store.removeTrack(playlistId, trackId)
    }

    override suspend fun reorderTracks(playlistId: String, trackIds: List<String>) {
        store.reorderTracks(playlistId, trackIds)
    }
}

private fun String.normalizePlaylistName(): String = trim().also { normalized ->
    require(normalized.isNotEmpty()) { "Playlist name must not be blank" }
    require(normalized.length <= 80) { "Playlist name must be 80 characters or fewer" }
}

private fun MusicPlaylistWithTracks.toDomain() = MusicPlaylist(
    id = playlist.id,
    name = playlist.name,
    createdAtMillis = playlist.createdAtMillis,
    tracks = tracks.sortedBy(MusicPlaylistTrackEntity::position).map(MusicPlaylistTrackEntity::toDomain),
)

private fun MusicPlaylistTrackEntity.toDomain() = MusicPlaylistTrack(
    trackId = trackId,
    uri = Uri.parse(uri),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
)
