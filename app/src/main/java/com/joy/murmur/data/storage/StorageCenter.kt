package com.joy.murmur.data.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.speech.TtsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageCenter(private val context: Context) {
    private val appContext = context.applicationContext
    private val db = PlaybackDatabase.getInstance(appContext)
    private val store = PlaybackStore(db)
    private val ttsCacheDir = File(appContext.cacheDir, "read-aloud")
    private val ttsCache = TtsCache(ttsCacheDir)
    private val neuralVoicesDir = File(appContext.filesDir, "neural-voices")
    private val exportsDir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "Murmur/Exports")
    private val tempDir = File(appContext.cacheDir, "temp")

    init {
        neuralVoicesDir.mkdirs()
        exportsDir.mkdirs()
        tempDir.mkdirs()
    }

    data class StorageUsage(
        val ttsCacheBytes: Long,
        val neuralVoicesBytes: Long,
        val exportsBytes: Long,
        val databaseBytes: Long,
        val tempBytes: Long,
        val totalBytes: Long,
        val availableBytes: Long,
    )

    suspend fun getStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val ttsCacheBytes = getDirectorySize(ttsCacheDir)
        val neuralVoicesBytes = getDirectorySize(neuralVoicesDir)
        val exportsBytes = getDirectorySize(exportsDir)
        val databaseBytes = getFileSize(File(db.openHelper.writableDatabase.path))
        val tempBytes = getDirectorySize(tempDir)
        val totalBytes = ttsCacheBytes + neuralVoicesBytes + exportsBytes + databaseBytes + tempBytes
        val availableBytes = getAvailableSpace()
        StorageUsage(ttsCacheBytes, neuralVoicesBytes, exportsBytes, databaseBytes, tempBytes, totalBytes, availableBytes)
    }

    private fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walk().filter { it.isFile }.sumOf { it.length() }
    }

    private fun getFileSize(file: File): Long {
        return if (file.exists()) file.length() else 0L
    }

    private fun getAvailableSpace(): Long {
        val stat = android.os.StatFs(appContext.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    suspend fun clearTtsCache(): Long = withContext(Dispatchers.IO) {
        val before = getDirectorySize(ttsCacheDir)
        ttsCacheDir.listFiles()?.forEach { it.deleteRecursively() }
        ttsCache.shutdown()
        before
    }

    suspend fun clearTempFiles(): Long = withContext(Dispatchers.IO) {
        val before = getDirectorySize(tempDir)
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    suspend fun clearNeuralVoices(): Long = withContext(Dispatchers.IO) {
        val before = getDirectorySize(neuralVoicesDir)
        neuralVoicesDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    suspend fun clearExports(): Long = withContext(Dispatchers.IO) {
        val before = getDirectorySize(exportsDir)
        exportsDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    fun getTtsCacheDir(): File = ttsCacheDir
    fun getNeuralVoicesDir(): File = neuralVoicesDir
    fun getExportsDir(): File = exportsDir
    fun getTempDir(): File = tempDir

    suspend fun exportDatabase(destinationUri: android.net.Uri): Result<android.net.Uri> = withContext(Dispatchers.IO) {
        try {
            val destFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(appContext, destinationUri)
                ?.createFile("application/vnd.sqlite3", "murmur.db")
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid destination"))
            
            val srcFile = File(db.openHelper.writableDatabase.path)
            appContext.contentResolver.openOutputStream(destFile.uri)?.use { output -> srcFile.inputStream().use { input -> input.copyTo(output) } }
            Result.success(destFile.uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importDatabase(sourceUri: android.net.Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val srcFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(appContext, sourceUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid source"))
            
            val destFile = File(db.openHelper.writableDatabase.path)
            appContext.contentResolver.openInputStream(srcFile.uri)?.use { input ->
                appContext.contentResolver.openOutputStream(android.net.Uri.fromFile(destFile))?.use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

