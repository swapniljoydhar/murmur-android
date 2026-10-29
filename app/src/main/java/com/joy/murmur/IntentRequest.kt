package com.joy.murmur

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * A one-shot request derived from the Intent that launched (or re-launched) [MainActivity].
 *
 * Parsing deliberately does no `ContentResolver` work (no `getType`, no cursor queries) so it can run
 * inside `onCreate` without blocking the first frame. Deciding what a URI actually *is* happens later,
 * off the main thread, in the UI layer.
 *
 * [DeepLink] carries the host and path segments rather than a navigation route so this file cannot drift
 * away from the navigation graph; `MurmurApp` owns the host-to-destination mapping.
 */
sealed interface MurmurIntentRequest {
    /** A `murmur://` link (app shortcut, share target, or browser hand-off). */
    data class DeepLink(val host: String, val pathSegments: List<String>) : MurmurIntentRequest

    /** Local audio and/or document URIs delivered through `ACTION_VIEW` / `ACTION_SEND` / `ACTION_SEND_MULTIPLE`. */
    data class OpenContent(val uris: List<Uri>) : MurmurIntentRequest
}

object MurmurIntents {
    const val SCHEME = "murmur"

    /** A misbehaving sender must not be able to push an unbounded list into the import path. */
    const val MAX_SHARED_URIS = 64

    fun parse(intent: Intent?): MurmurIntentRequest? {
        if (intent == null) return null
        val data = intent.data
        MurmurIntentRules.deepLink(
            scheme = data?.scheme,
            host = data?.host,
            pathSegments = data?.pathSegments.orEmpty(),
        )?.let { return it }

        val uris = MurmurIntentRules.sharedUris(
            action = intent.action,
            candidates = buildList<String> {
                // ACTION_SEND carries a single Parcelable (or, from some senders, only ClipData);
                // ACTION_SEND_MULTIPLE is the only action that uses an ArrayList.
                when (intent.action) {
                    Intent.ACTION_SEND -> singleStream(intent)?.let(::add)
                    Intent.ACTION_SEND_MULTIPLE -> addAll(multipleStreams(intent))
                    Intent.ACTION_VIEW -> data?.toString()?.let(::add)
                }
                clipDataUris(intent).forEach(::add)
            },
        )
        return if (uris.isEmpty()) null else MurmurIntentRequest.OpenContent(uris.map(Uri::parse))
    }

    private fun singleStream(intent: Intent): String? {
        val extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        return extra?.toString()
    }

    private fun multipleStreams(intent: Intent): List<String> {
        val extras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }.orEmpty()
        return extras.mapNotNull { it.toString() }
    }

    private fun clipDataUris(intent: Intent): List<String> {
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri?.toString() }
    }
}

/**
 * Pure parsing rules, kept free of Android framework objects so they can be covered by JVM unit tests.
 */
internal object MurmurIntentRules {
    const val MAX_SHARED_URIS = MurmurIntents.MAX_SHARED_URIS

    /** Returns a deep link only for the app's own scheme with a non-blank host. */
    fun deepLink(scheme: String?, host: String?, pathSegments: List<String>): MurmurIntentRequest.DeepLink? {
        if (scheme != MurmurIntents.SCHEME) return null
        val safeHost = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return MurmurIntentRequest.DeepLink(host = safeHost, pathSegments = pathSegments)
    }

    /** Keeps only readable local URIs for the actions Murmur accepts, de-duplicated and capped. */
    fun sharedUris(action: String?, candidates: List<String?>): List<String> {
        if (action !in ACCEPTED_ACTIONS) return emptyList()
        return candidates
            .asSequence()
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .filter(::isLocalReadableUri)
            .distinct()
            .take(MAX_SHARED_URIS)
            .toList()
    }

    // Literals (not Intent.ACTION_* constants) so this object stays loadable in JVM unit tests.
    private val ACCEPTED_ACTIONS = setOf(
        "android.intent.action.VIEW",
        "android.intent.action.SEND",
        "android.intent.action.SEND_MULTIPLE",
    )

    private fun isLocalReadableUri(value: String): Boolean {
        val scheme = value.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme == "content" || scheme == "file"
    }
}
