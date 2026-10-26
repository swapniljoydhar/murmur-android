package com.joy.murmur.data.speech

import kotlinx.serialization.Serializable

@Serializable
data class ModelManifest(
    val id: String,
    val name: String,
    val language: String,
    val languageTag: String,
    val version: String,
    val tokenizer: TokenizerInfo,
    val modelFiles: List<ModelFileInfo>,
    val voiceMetadata: VoiceMetadata,
    val licenses: List<LicenseInfo>,
    val checksums: Map<String, String>,
)

@Serializable
data class TokenizerInfo(
    val type: String,
    val vocabFile: String,
    val tokensFile: String?,
    val addBlank: Boolean = true,
    val bpeVocabFile: String?,
    val bpeTokensFile: String?,
)

@Serializable
data class ModelFileInfo(
    val fileName: String,
    val fileSize: Long,
    val isRequired: Boolean = true,
    val description: String,
)

@Serializable
data class VoiceMetadata(
    val sampleRate: Int,
    val numSpeakers: Int,
    val isMultiLingual: Boolean,
    val speakerNames: List<String>?,
    val defaultSpeaker: Int,
    val speakerEmbeddingDim: Int? = null,
)

@Serializable
data class LicenseInfo(
    val name: String,
    val url: String,
    val spdxId: String?,
)

@Serializable
data class ModelIndex(
    val version: Int,
    val models: List<ModelSummary>,
    val updatedAt: Long,
)

@Serializable
data class ModelSummary(
    val id: String,
    val name: String,
    val language: String,
    val languageTag: String,
    val version: String,
    val downloadUrl: String,
    val totalSize: Long,
    val voiceMetadata: VoiceMetadata,
    val licenses: List<LicenseInfo>,
)