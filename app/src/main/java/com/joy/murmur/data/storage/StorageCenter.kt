package com.joy.murmur.data.storage

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.StatFs
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.joy.murmur.data.playback.PlaybackDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class StorageCenter(context: Context, private val db: PlaybackDatabase = PlaybackDatabase.getInstance(context)) {
    private val appContext = context.applicationContext
    private val databaseFile = File(db.openHelper.writableDatabase.path ?: appContext.getDatabasePath("murmur.db").absolutePath)
    private val ttsCacheDir = File(appContext.cacheDir, "read-aloud")
    private val tempDir = File(appContext.cacheDir, "temp")
    private val databaseTables = listOf(
        "listening_progress",
        "audiobook_library",
        "audiobook_bookmarks",
        "audiobook_chapters",
        "music_catalog",
        "music_catalog_state",
        "music_playlists",
        "music_playlist_tracks",
        "reader_documents",
        "reader_bookmarks",
        "document_structures",
        "read_aloud_segment_map",
    )

    data class StorageUsage(
        val ttsCacheBytes: Long,
        val databaseBytes: Long,
        val tempBytes: Long,
        val totalBytes: Long,
        val availableBytes: Long,
    )

    suspend fun clearGeneratedSpeechCache(): Long = withContext(Dispatchers.IO) {
        val dao = db.playbackDao()
        val protectedNames = dao.getQueue().mapNotNull { item ->
            Uri.parse(item.uri).takeIf { it.scheme == "file" }?.lastPathSegment
        }.toSet()
        val protectedKeys = protectedNames.map { it.removeSuffix(".wav") }.toSet()
        var reclaimed = 0L
        if (ttsCacheDir.isDirectory) {
            ttsCacheDir.walkBottomUp().forEach { file ->
                if (file.isFile && file.name !in protectedNames) {
                    val length = file.length()
                    if (file.delete()) reclaimed += length
                }
            }
        }
        dao.getAllTtsCacheEntries().forEach { entry ->
            if (entry.cacheKey !in protectedKeys || !File(entry.filePath).isFile) {
                dao.deleteTtsCacheEntry(entry.cacheKey)
            }
        }
        if (tempDir.isDirectory) {
            tempDir.walkBottomUp().forEach { file ->
                if (file.isFile) {
                    val length = file.length()
                    if (file.delete()) reclaimed += length
                }
            }
        }
        reclaimed
    }

    suspend fun getStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        checkpointWal()
        val ttsCacheBytes = directorySize(ttsCacheDir)
        val databaseBytes = listOf(databaseFile, File("${databaseFile.path}-wal"), File("${databaseFile.path}-shm"))
            .sumOf(::fileSize)
        val tempBytes = directorySize(tempDir)
        StorageUsage(
            ttsCacheBytes = ttsCacheBytes,
            databaseBytes = databaseBytes,
            tempBytes = tempBytes,
            totalBytes = ttsCacheBytes + databaseBytes + tempBytes,
            availableBytes = StatFs(appContext.filesDir.absolutePath).let { it.availableBlocksLong * it.blockSizeLong },
        )
    }

    suspend fun createBackup(destination: Uri) = withContext(Dispatchers.IO) {
        val bytes = createBackupBytesInternal()
        val target = DocumentFile.fromSingleUri(appContext, destination)
            ?: throw IllegalArgumentException("Choose a writable backup file.")
        try {
            val output = appContext.contentResolver.openOutputStream(target.uri, "wt")
                ?: throw IllegalStateException("The selected location could not be written.")
            output.use { it.write(bytes); it.flush() }
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
        inspectBackupBytes(bytes)
    }

    internal suspend fun createBackupBytes() = withContext(Dispatchers.IO) { createBackupBytesInternal() }

    private suspend fun createBackupBytesInternal(): ByteArray {
        checkpointWal()
        val content = db.withTransaction {
            val sqlDb = db.openHelper.writableDatabase
            val tableData = databaseTables.associateWith { table ->
                val columns = tableColumns(table)
                val rows = sqlDb.query("SELECT * FROM ${quote(table)} ORDER BY rowid").use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(List(cursor.columnCount) { index -> cursor.cell(index) })
                    }
                }
                MurmurBackupTable(columns, rows)
            }
            MurmurBackupContent(sqlDb.version, System.currentTimeMillis(), tableData)
        }
        return MurmurBackupFormat.encode(content)
    }

    suspend fun inspectBackup(source: Uri) = withContext(Dispatchers.IO) {
        inspectBackupBytes(readBackup(source))
    }

    internal suspend fun inspectBackupBytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val envelope = MurmurBackupFormat.decode(bytes, db.openHelper.writableDatabase.version)
        validateSchema(envelope.content)
        validateCellValues(envelope.content)
        MurmurBackupFormat.summary(envelope.content)
    }

    suspend fun restoreBackup(source: Uri) = withContext(Dispatchers.IO) {
        restoreBackupBytesInternal(readBackup(source))
    }

    internal suspend fun restoreBackupBytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        restoreBackupBytesInternal(bytes)
    }

    private suspend fun restoreBackupBytesInternal(bytes: ByteArray): MurmurBackupSummary {
        val content = MurmurBackupFormat.decode(bytes, db.openHelper.writableDatabase.version).content
        validateSchema(content)
        val preparedRows = validateCellValues(content)
        require(db.playbackDao().getQueue().isEmpty()) { "Clear the playback queue before restoring." }
        db.withTransaction {
            val sqlDb = db.openHelper.writableDatabase
            sqlDb.query("SELECT COUNT(*) FROM playback_queue").use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 0L) { "Clear the playback queue before restoring." }
            }
            val dependencyOrder = foreignKeyOrder()
            dependencyOrder.asReversed().forEach { table -> sqlDb.execSQL("DELETE FROM ${quote(table)}") }
            dependencyOrder.forEach { table ->
                preparedRows.getValue(table).forEach { values ->
                    check(sqlDb.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values) != -1L) {
                        "Could not restore a record in $table."
                    }
                }
            }
            sqlDb.query("PRAGMA foreign_key_check").use { cursor ->
                check(!cursor.moveToFirst()) { "Backup contains broken record links." }
            }
        }
        checkpointWal()
        return MurmurBackupFormat.summary(content)
    }

    private fun checkpointWal() {
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        }
    }

    private fun readBackup(uri: Uri): ByteArray {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("The selected backup could not be opened.")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MurmurBackupFormat.MAX_BACKUP_BYTES) throw IllegalArgumentException("Backup exceeds the 64 MiB size limit.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun validateSchema(content: MurmurBackupContent) {
        require(content.tables.keys == databaseTables.toSet()) { "Backup tables do not match this Murmur database." }
        content.tables.forEach { (table, data) ->
            require(data.columns == tableColumns(table)) { "Backup table structure differs for $table." }
        }
    }

    private fun validateCellValues(content: MurmurBackupContent): Map<String, List<ContentValues>> =
        content.tables.mapValues { (_, table) -> table.rows.map { row ->
            ContentValues(table.columns.size).apply {
                table.columns.forEachIndexed { index, column ->
                    val cell = row[index]
                    when (cell.type) {
                        "null" -> putNull(column)
                        "integer" -> put(column, cell.value!!.toLong())
                        "real" -> put(column, cell.value!!.toDouble().also { require(it.isFinite()) })
                        "text" -> put(column, cell.value!!)
                        "blob" -> put(column, Base64.decode(cell.value!!, Base64.NO_WRAP))
                        else -> error("Unsupported backup value type.")
                    }
                }
            }
        } }

    private fun tableColumns(table: String): List<String> {
        require(table in databaseTables)
        return db.openHelper.readableDatabase.query("PRAGMA table_info(${quote(table)})").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }
    }

    private fun foreignKeyOrder(): List<String> {
        val parents = databaseTables.associateWith { table ->
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_list(${quote(table)})").use { cursor ->
                val parentIndex = cursor.getColumnIndexOrThrow("table")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(parentIndex)) }
            }
        }
        val ordered = mutableListOf<String>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(table: String) {
            if (table in visited) return
            check(visiting.add(table)) { "Database contains a cyclic foreign-key relationship." }
            parents.getValue(table).filter { it in databaseTables }.forEach(::visit)
            visiting.remove(table)
            visited.add(table)
            ordered.add(table)
        }
        databaseTables.forEach(::visit)
        return ordered
    }

    private fun quote(identifier: String) = "\"${identifier.replace("\"", "\"\"")}\""

    private fun Cursor.cell(index: Int): MurmurBackupCell = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> MurmurBackupCell("null")
        Cursor.FIELD_TYPE_INTEGER -> MurmurBackupCell("integer", getLong(index).toString())
        Cursor.FIELD_TYPE_FLOAT -> MurmurBackupCell("real", getDouble(index).toString())
        Cursor.FIELD_TYPE_STRING -> MurmurBackupCell("text", getString(index))
        Cursor.FIELD_TYPE_BLOB -> MurmurBackupCell("blob", Base64.encodeToString(getBlob(index), Base64.NO_WRAP))
        else -> error("Unsupported SQLite value type.")
    }

    private fun directorySize(directory: File): Long =
        if (directory.isDirectory) directory.walkTopDown().filter(File::isFile).sumOf(::fileSize) else 0L

    private fun fileSize(file: File): Long = file.takeIf(File::isFile)?.length() ?: 0L
}
