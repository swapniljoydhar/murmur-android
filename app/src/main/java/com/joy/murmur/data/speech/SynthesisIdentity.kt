package com.joy.murmur.data.speech

/**
 * Everything that can change the bytes the speech engine produces for the same text.
 *
 * It is serialised into the cache key of generated audio, so a voice, locale or post-processing change
 * produces a different file instead of replaying stale audio.
 */
public data class SynthesisIdentity(
    val engine: String,
    val engineVersion: String,
    val voice: String,
    val locale: String,
    val pronunciationVersion: String,
    val filterVersion: String,
    val sampleRate: Int,
    val channelCount: Int,
    val bitDepth: Int,
) {
    override fun toString(): String =
        "$engine|$engineVersion|$voice|$locale|$pronunciationVersion|$filterVersion|$sampleRate|$channelCount|$bitDepth"
}
