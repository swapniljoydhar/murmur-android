package com.joy.murmur.data.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class MurmurBackupContent(
    val databaseVersion: Int,
    val createdAtMillis: Long,
    val tables: Map<String, MurmurBackupTable>,
)

@Serializable
data class MurmurBackupTable(
    val columns: List<String>,
    val rows: List<List<MurmurBackupCell>>,
)

@Serializable
data class MurmurBackupCell(val type: String, val value: String? = null)

@Serializable
data class MurmurBackupEnvelope(
    val formatVersion: Int,
    val content: MurmurBackupContent,
    val sha256: String,
)

data class MurmurBackupSummary(
    val createdAtMillis: Long,
    val recordsByTable: Map<String, Int>,
    val totalRecords: Int,
)

class MurmurBackupFormatException(message: String) : Exception(message)

object MurmurBackupFormat {
    const val CURRENT_VERSION = 1
    const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private const val MAX_TABLES = 64
    private const val MAX_ROWS = 500_000
    private const val MAX_COLUMNS = 128
    private const val MAX_CELL_CHARS = 8 * 1024 * 1024
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    fun encode(content: MurmurBackupContent): ByteArray {
        validateContent(content)
        val canonicalContent = json.encodeToString(content)
        val checksum = sha256(canonicalContent.toByteArray(Charsets.UTF_8))
        val bytes = json.encodeToString(MurmurBackupEnvelope(CURRENT_VERSION, content, checksum))
            .toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BACKUP_BYTES) throw MurmurBackupFormatException("Backup exceeds the 64 MiB size limit.")
        return bytes
    }

    fun decode(bytes: ByteArray, expectedDatabaseVersion: Int? = null): MurmurBackupEnvelope {
        if (bytes.size > MAX_BACKUP_BYTES) throw MurmurBackupFormatException("Backup exceeds the 64 MiB size limit.")
        val envelope = try {
            json.decodeFromString<MurmurBackupEnvelope>(bytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            throw MurmurBackupFormatException("This file is not a valid Murmur backup.")
        }
        if (envelope.formatVersion != CURRENT_VERSION) throw MurmurBackupFormatException("This Murmur backup version is not supported.")
        if (expectedDatabaseVersion != null && envelope.content.databaseVersion != expectedDatabaseVersion) {
            throw MurmurBackupFormatException("This backup uses a different database version. Restore it with a compatible Murmur version.")
        }
        validateContent(envelope.content)
        val canonicalContent = json.encodeToString(envelope.content)
        if (!MessageDigest.isEqual(
                envelope.sha256.lowercase().toByteArray(Charsets.US_ASCII),
                sha256(canonicalContent.toByteArray(Charsets.UTF_8)).toByteArray(Charsets.US_ASCII),
            )
        ) throw MurmurBackupFormatException("Backup checksum does not match; no data was restored.")
        return envelope
    }

    fun summary(content: MurmurBackupContent) = MurmurBackupSummary(
        createdAtMillis = content.createdAtMillis,
        recordsByTable = content.tables.mapValues { it.value.rows.size },
        totalRecords = content.tables.values.sumOf { it.rows.size },
    )

    private fun validateContent(content: MurmurBackupContent) {
        if (content.databaseVersion <= 0) throw MurmurBackupFormatException("Backup database version is invalid.")
        if (content.tables.isEmpty() || content.tables.size > MAX_TABLES) throw MurmurBackupFormatException("Backup table count is invalid.")
        if (content.tables.values.sumOf { it.rows.size } > MAX_ROWS) throw MurmurBackupFormatException("Backup contains too many records.")
        content.tables.forEach { (name, table) ->
            if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) throw MurmurBackupFormatException("Backup contains an invalid table name.")
            if (table.columns.isEmpty() || table.columns.size > MAX_COLUMNS || table.columns.distinct().size != table.columns.size) {
                throw MurmurBackupFormatException("Backup table columns are invalid.")
            }
            if (table.rows.any { it.size != table.columns.size }) throw MurmurBackupFormatException("Backup row does not match its table columns.")
            if (table.rows.flatten().any { cell -> cell.type !in setOf("null", "integer", "real", "text", "blob") || (cell.value?.length ?: 0) > MAX_CELL_CHARS }) {
                throw MurmurBackupFormatException("Backup contains an invalid or oversized value.")
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
