package com.joy.murmur.domain.documents

data class DocumentStructure(
    val documentId: String,
    val mimeType: String,
    val totalChars: Long,
    val totalLines: Long,
    val sections: List<Section>,
    val fingerprint: String,
    val indexVersion: Int,
) {
    companion object {
        fun empty(documentId: String, mimeType: String): DocumentStructure = DocumentStructure(
            documentId = documentId,
            mimeType = mimeType,
            totalChars = 0,
            totalLines = 0,
            sections = emptyList(),
            fingerprint = "",
            indexVersion = 1,
        )
    }

    fun findSectionAt(charPosition: Long): Section? {
        var low = 0
        var high = sections.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val section = sections[mid]
            if (charPosition < section.startChar) {
                high = mid - 1
            } else if (charPosition > section.endChar) {
                low = mid + 1
            } else {
                return section
            }
        }
        val insertIndex = low
        return if (insertIndex < sections.size) sections[insertIndex] else sections.lastOrNull()
    }

    fun findSectionById(sectionId: String): Section? = sections.find { it.id == sectionId }
}

data class Section(
    val id: String,
    val title: String,
    val startChar: Long,
    val endChar: Long,
    val startLine: Long,
    val level: Int,
) {
    val charLength: Long
        get() = endChar - startChar
}