package com.joy.murmur.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MurmurBackupFormatTest {
    private val content = MurmurBackupContent(
        databaseVersion = 15,
        createdAtMillis = 1_700_000_000_000,
        tables = mapOf("sample_table" to MurmurBackupTable(
            columns = listOf("id", "title"),
            rows = listOf(listOf(MurmurBackupCell("integer", "7"), MurmurBackupCell("text", "Book"))),
        )),
    )

    @Test
    fun roundTripsVersionedContentAndSummary() {
        val restored = MurmurBackupFormat.decode(MurmurBackupFormat.encode(content), expectedDatabaseVersion = 15)

        assertEquals(content, restored.content)
        assertEquals(1, MurmurBackupFormat.summary(restored.content).totalRecords)
    }

    @Test
    fun rejectsChecksumMismatch() {
        val encoded = MurmurBackupFormat.encode(content).toString(Charsets.UTF_8)
        val tampered = encoded.replace(Regex("\"sha256\":\"[0-9a-f]{64}\""), "\"sha256\":\"${"0".repeat(64)}\"")

        assertThrows(MurmurBackupFormatException::class.java) {
            MurmurBackupFormat.decode(tampered.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun rejectsDifferentDatabaseVersionBeforeRestore() {
        val bytes = MurmurBackupFormat.encode(content)

        assertThrows(MurmurBackupFormatException::class.java) {
            MurmurBackupFormat.decode(bytes, expectedDatabaseVersion = 14)
        }
    }
}
