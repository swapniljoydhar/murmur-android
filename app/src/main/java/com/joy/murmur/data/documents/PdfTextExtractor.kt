package com.joy.murmur.data.documents

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor

/**
 * PDF text extraction utility using Android's PdfTextExtractor (API 29+)
 * Falls back gracefully on older API levels.
 * Uses reflection to access PdfRenderer.Page.getText() which was added in API 29.
 */
internal object PdfTextExtractor {
    
    /**
     * Extracts text from a specific page of a PDF document.
     * Returns null if text extraction is not supported on this API level or fails.
     */
    fun extractPageText(
        context: Context,
        uri: android.net.Uri,
        pageIndex: Int
    ): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        extractPageTextApi29(context, uri, pageIndex)
    } else {
        null
    }

    /**
     * Extracts text from all pages of a PDF document for searching.
     * Returns concatenated text from all pages, or null if not supported.
     */
    fun extractAllText(
        context: Context,
        uri: android.net.Uri
    ): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        extractAllTextApi29(context, uri)
    } else {
        null
    }

    private fun extractPageTextApi29(
        context: Context,
        uri: android.net.Uri,
        pageIndex: Int
    ): String? {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return null
        val renderer = PdfRenderer(descriptor)
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            renderer.close()
            descriptor.close()
            return null
        }
        return try {
            val page = renderer.openPage(pageIndex)
            try {
                // Use reflection to call PdfRenderer.Page.getText() (API 29+)
                val text = getPageText(page)
                if (text.isNullOrBlank()) null else text.trim()
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private fun extractAllTextApi29(
        context: Context,
        uri: android.net.Uri
    ): String? {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return null
        val renderer = PdfRenderer(descriptor)
        return try {
            val builder = StringBuilder()
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                try {
                    getPageText(page)?.let { builder.append(it).append("\n\n") }
                } finally {
                    page.close()
                }
            }
            val result = builder.toString().trim()
            if (result.isBlank()) null else result
        } catch (e: Exception) {
            null
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    /**
     * Uses reflection to call PdfRenderer.Page.getText() which was added in API 29.
     * Returns null if the method doesn't exist or fails.
     */
    private fun getPageText(page: PdfRenderer.Page): String? {
        return try {
            val method = PdfRenderer.Page::class.java.getMethod("getText")
            method.invoke(page) as? String
        } catch (e: Exception) {
            null
        }
    }
}