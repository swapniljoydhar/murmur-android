package com.joy.murmur.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local Storage Access Framework (SAF) audio file & folder resolver built on `DocumentFile`.
 * Grants persistable read URI permissions and traverses local folders safely with bounded depth and count.
 */
object DocumentFileAudioImporter {
    const val MAX_FOLDER_AUDIO_FILES = 500
    const val MAX_FOLDER_DEPTH = 6

    val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav",
    )

    data class ImportedFolderResult(
        val folderName: String,
        val audioUris: List<Uri>,
        val reachedFileCap: Boolean,
    )

    fun isSupportedAudioDocument(mimeType: String?, displayName: String?): Boolean {
        val normalizedMime = mimeType?.lowercase().orEmpty()
        if (normalizedMime.startsWith("audio/")) return true
        val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return extension in SUPPORTED_AUDIO_EXTENSIONS &&
            (normalizedMime.isEmpty() || normalizedMime == "application/octet-stream" || normalizedMime == "application/ogg" || normalizedMime == "video/mp4")
    }

    suspend fun resolveAudioFilesFromTree(
        context: Context,
        treeUri: Uri,
        maxFiles: Int = MAX_FOLDER_AUDIO_FILES,
        maxDepth: Int = MAX_FOLDER_DEPTH,
    ): ImportedFolderResult = withContext(Dispatchers.IO) {
        persistReadPermission(context, treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportedFolderResult("Selected folder", emptyList(), false)
        val folderName = root.name?.takeIf(String::isNotBlank) ?: "Imported folder"
        val collected = mutableListOf<Uri>()
        var capped = false

        fun traverse(directory: DocumentFile, depth: Int) {
            if (depth > maxDepth || collected.size >= maxFiles) {
                if (collected.size >= maxFiles) capped = true
                return
            }
            val children = directory.listFiles()
                .sortedBy { it.name?.lowercase().orEmpty() }
            for (child in children) {
                if (collected.size >= maxFiles) {
                    capped = true
                    return
                }
                if (child.isDirectory) {
                    traverse(child, depth + 1)
                } else if (child.isFile && child.canRead() && isSupportedAudioDocument(child.type, child.name)) {
                    collected += child.uri
                }
            }
        }

        traverse(root, depth = 0)
        ImportedFolderResult(
            folderName = folderName,
            audioUris = collected.distinctBy(Uri::toString),
            reachedFileCap = capped,
        )
    }

    suspend fun resolveAudioFilesFromSingleUris(
        context: Context,
        uris: List<Uri>,
    ): List<Uri> = withContext(Dispatchers.IO) {
        uris.take(MAX_FOLDER_AUDIO_FILES).mapNotNull { uri ->
            persistReadPermission(context, uri)
            val doc = DocumentFile.fromSingleUri(context, uri)
            if (doc != null && doc.exists() && doc.canRead() && isSupportedAudioDocument(doc.type, doc.name)) {
                doc.uri
            } else {
                val fallbackMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                if (isSupportedAudioDocument(fallbackMime, uri.lastPathSegment)) uri else null
            }
        }.distinctBy(Uri::toString)
    }

    fun persistReadPermission(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return true
        return runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)
    }
}
