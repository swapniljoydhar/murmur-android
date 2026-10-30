package com.joy.murmur

import android.content.Intent
import android.net.Uri
import com.joy.murmur.data.documents.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityIntentRoutingTest {

    @Test
    fun `deep link murmur urls map to expected Compose routes without crashing Activity`() {
        val cases = mapOf(
            "murmur://continue" to "continue",
            "murmur://music" to "music",
            "murmur://audiobooks" to "audiobooks",
            "murmur://documents" to "documents",
            "murmur://player" to "now-playing",
            "murmur://reader/document/doc-42" to "reader/doc-42",
        )

        cases.forEach { (url, expectedRoute) ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val parsed = parseIncomingIntent(intent) { null }
            assertEquals(IncomingIntentAction.Navigate(expectedRoute), parsed)
        }

        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("murmur://music"))
        val controller = Robolectric.buildActivity(MainActivity::class.java, launchIntent).create()
        val activity = controller.get()
        assertEquals("music", activity.pendingNavigationRoute.value)
        activity.consumePendingRoute()
        assertNull(activity.pendingNavigationRoute.value)
        controller.destroy()
    }

    @Test
    fun `ACTION_SEND and ACTION_SEND_MULTIPLE extract supported URIs and cap batch size at 100`() {
        val docUri = Uri.parse("content://com.android.providers.downloads/document/book.epub")
        val audioUri = Uri.parse("content://com.android.providers.media/audio/song.flac")
        val remoteHttpUri = Uri.parse("https://example.com/malicious.pdf")

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/epub+zip"
            putExtra(Intent.EXTRA_STREAM, docUri)
        }
        val singleParsed = parseIncomingIntent(sendIntent) { "application/epub+zip" }
        assertTrue(singleParsed is IncomingIntentAction.OpenSharedContent)
        val singleContent = singleParsed as IncomingIntentAction.OpenSharedContent
        assertEquals(listOf(docUri), singleContent.documentUris)
        assertTrue(singleContent.audioUris.isEmpty())

        val multiIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(docUri, audioUri, remoteHttpUri))
        }
        val multiParsed = parseIncomingIntent(multiIntent) { uri ->
            when (uri) {
                docUri -> "application/epub+zip"
                audioUri -> "audio/flac"
                else -> "application/pdf"
            }
        }
        assertTrue(multiParsed is IncomingIntentAction.OpenSharedContent)
        val multiContent = multiParsed as IncomingIntentAction.OpenSharedContent
        assertEquals(listOf(docUri), multiContent.documentUris)
        assertEquals(listOf(audioUri), multiContent.audioUris)

        // Verify abusive ACTION_SEND_MULTIPLE payload is capped at MAX_SHARED_URIS (100)
        val oversizedList = ArrayList<Uri>(150).apply {
            repeat(150) { index ->
                add(Uri.parse("content://com.android.providers.media/audio/track_$index.mp3"))
            }
        }
        val oversizedIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, oversizedList)
        }
        val cappedParsed = parseIncomingIntent(oversizedIntent) { "audio/mpeg" } as IncomingIntentAction.OpenSharedContent
        assertEquals(MAX_SHARED_URIS, cappedParsed.audioUris.size)
    }

    @Test
    fun `EpubParser isTocEntryName matches exact toc and nav filenames without matching chapter substrings`() {
        assertTrue(EpubParser.isTocEntryName("OEBPS/toc.ncx"))
        assertTrue(EpubParser.isTocEntryName("EPUB/Text/toc.xhtml"))
        assertTrue(EpubParser.isTocEntryName("EPUB/nav.xhtml"))
        assertTrue(EpubParser.isTocEntryName("toc.html"))

        assertFalse(EpubParser.isTocEntryName("OEBPS/Text/stock.xhtml"))
        assertFalse(EpubParser.isTocEntryName("OEBPS/Text/protocol.html"))
        assertFalse(EpubParser.isTocEntryName("OEBPS/Text/aristocracy.htm"))
    }
}
