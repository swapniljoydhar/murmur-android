package com.joy.murmur.domain.music

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class MusicPlaylistTrack(
    val trackId: String,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
)

data class MusicPlaylist(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val tracks: List<MusicPlaylistTrack>,
)

interface MusicPlaylistRepository {
    fun observePlaylists(): Flow<List<MusicPlaylist>>
    suspend fun createPlaylist(name: String): MusicPlaylist
    suspend fun renamePlaylist(playlistId: String, name: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTrack(playlistId: String, track: MusicPlaylistTrack)
    suspend fun removeTrack(playlistId: String, trackId: String)
    suspend fun reorderTracks(playlistId: String, trackIds: List<String>)
}
