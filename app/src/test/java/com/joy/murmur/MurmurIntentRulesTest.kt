package com.joy.murmur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MurmurIntentRulesTest {

    @Test
    fun deepLinkRequiresOwnSchemeAndHost() {
        assertNull(MurmurIntentRules.deepLink("https", "music", emptyList()))
        assertNull(MurmurIntentRules.deepLink("murmur", null, emptyList()))
        assertNull(MurmurIntentRules.deepLink("murmur", "   ", emptyList()))
        assertNull(MurmurIntentRules.deepLink(null, "music", emptyList()))
    }

    @Test
    fun deepLinkKeepsHostAndPathSegments() {
        val link = MurmurIntentRules.deepLink("murmur", "reader", listOf("", "document:abc"))
        assertEquals("reader", link?.host)
        assertEquals(listOf("", "document:abc"), link?.pathSegments)
    }

    @Test
    fun sendWithSingleStreamIsAccepted() {
        val uris = MurmurIntentRules.sharedUris(
            action = "android.intent.action.SEND",
            candidates = listOf("content://com.example.provider/song.mp3"),
        )
        assertEquals(listOf("content://com.example.provider/song.mp3"), uris)
    }

    @Test
    fun viewDataAndClipDataBothContribute() {
        val uris = MurmurIntentRules.sharedUris(
            action = "android.intent.action.VIEW",
            candidates = listOf(
                "content://provider/book.epub",
                "content://provider/book.epub",
                "file:///storage/emulated/0/notes.txt",
            ),
        )
        assertEquals(
            listOf("content://provider/book.epub", "file:///storage/emulated/0/notes.txt"),
            uris,
        )
    }

    @Test
    fun remoteAndUnknownSchemesAreRejected() {
        val uris = MurmurIntentRules.sharedUris(
            action = "android.intent.action.SEND_MULTIPLE",
            candidates = listOf(
                "https://example.com/track.mp3",
                "mailto:someone@example.com",
                "  ",
                null,
                "content://provider/ok.flac",
            ),
        )
        assertEquals(listOf("content://provider/ok.flac"), uris)
    }

    @Test
    fun unrelatedActionsYieldNothing() {
        assertEquals(
            emptyList<String>(),
            MurmurIntentRules.sharedUris("android.intent.action.MAIN", listOf("content://provider/a.mp3")),
        )
    }

    @Test
    fun sharedUrisAreCapped() {
        val many = (1..200).map { "content://provider/file$it.mp3" }
        val uris = MurmurIntentRules.sharedUris("android.intent.action.SEND_MULTIPLE", many)
        assertEquals(MurmurIntentRules.MAX_SHARED_URIS, uris.size)
        assertEquals("content://provider/file1.mp3", uris.first())
    }
}
