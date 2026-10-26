package com.joy.murmur

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joy.murmur.data.documents.PdfTextExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class PdfTextExtractorTest {
    @Test
    fun readsSelectableTextAndSearchResultsNavigateToTheCorrectPage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfFile = File(context.cacheDir, "pdf-text-${System.nanoTime()}.pdf")
        writePdf(pdfFile, listOf("first page", "Murmur searchable text"))

        try {
            val uri = Uri.fromFile(pdfFile)
            val pageText = PdfTextExtractor.extractPageText(context, uri, 1)
            val matches = PdfTextExtractor.searchDocument(context, uri, "searchable", limit = 10)

            assertTrue(pageText.orEmpty().contains("Murmur searchable text", ignoreCase = true))
            assertEquals(1L, matches?.singleOrNull()?.positionChars)
            assertTrue(matches?.singleOrNull()?.snippet.orEmpty().contains("searchable", ignoreCase = true))
        } finally {
            pdfFile.delete()
        }
    }

    @Test
    fun scannedPageReturnsNoText() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfFile = File(context.cacheDir, "pdf-image-${System.nanoTime()}.pdf")
        writePdf(pdfFile, listOf(""))

        try {
            val text = PdfTextExtractor.extractPageText(context, Uri.fromFile(pdfFile), 0)
            assertEquals(null, text)
        } finally {
            pdfFile.delete()
        }
    }

    private fun writePdf(destination: File, pageTexts: List<String>) {
        val document = PdfDocument()
        try {
            pageTexts.forEachIndexed { index, text ->
                val pageInfo = PdfDocument.PageInfo.Builder(612, 792, index + 1).create()
                val page = document.startPage(pageInfo)
                if (text.isNotEmpty()) {
                    page.canvas.drawText(text, 48f, 72f, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f })
                }
                document.finishPage(page)
            }
            FileOutputStream(destination).use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
