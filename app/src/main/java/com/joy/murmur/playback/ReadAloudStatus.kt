package com.joy.murmur.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReadAloudFailure(val documentId: String, val message: String)

/** Process-local status for reporting a failed service-side Read Aloud continuation to the UI. */
object ReadAloudStatus {
    private val mutableFailure = MutableStateFlow<ReadAloudFailure?>(null)
    val failure: StateFlow<ReadAloudFailure?> = mutableFailure.asStateFlow()

    fun reportFailure(documentId: String, message: String) {
        mutableFailure.value = ReadAloudFailure(
            documentId = documentId,
            message = message.takeIf(String::isNotBlank) ?: "Murmur couldn't continue Read Aloud.",
        )
    }

    fun clearFailure(documentId: String? = null) {
        val current = mutableFailure.value ?: return
        if (documentId == null || current.documentId == documentId) mutableFailure.value = null
    }
}
