package com.iab.omid.sampleapp.manager

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Represents a single WebVTT caption cue.
 *
 * @property startMs Inclusive start time (milliseconds from media start).
 * @property endMs Exclusive end time (cue active while startMs <= t < endMs).
 * @property text Caption payload.
 */
data class ClosedCaptionsCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Lightweight side‑car WebVTT caption manager.
 *
 * Responsibilities:
 *  - Parse WebVTT content from a content [Uri] or [InputStream] into an immutable, time‑sorted list of cues.
 *  - Provide efficient caption lookup for an arbitrary playback position.
 */
class ClosedCaptionsManager {

    @Volatile
    private var cues: List<ClosedCaptionsCue> = emptyList()

    /**
     * Loads and parses a WebVTT file referenced by an Android [Uri].
     *
     * @param context Context used to resolve the content Uri.
     * @param uri Uri pointing to a readable VTT resource.
     * @throws IllegalArgumentException if the Uri cannot be opened.
     *
     * Execution is dispatched to IO to avoid blocking the caller's thread.
     */
    suspend fun load(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { load(it) }
                ?: throw IllegalArgumentException("Cannot open Uri: $uri")
        }
    }

    /**
     * Parses WebVTT caption data from an [InputStream].
     *
     * Parsing strategy:
     *  - Read full stream into memory (String).
     *  - Split on blank lines to obtain cue blocks.
     *  - For each block: first non‑header line must be the timing line "start --> end".
     *  - Convert timestamps to milliseconds; skip if invalid or reversed.
     *  - Remaining lines joined with '\n' form the caption text.
     *  - Resulting cues sorted ascending by startMs and published atomically.
     *
     * Malformed blocks are ignored; no exception is thrown unless stream reading fails externally.
     */
    fun load(inputStream: InputStream) {
        val raw = inputStream.bufferedReader().use { it.readText() }
        val blocks = raw.split(Regex("\\r?\\n\\r?\\n"))
        val parsed = ArrayList<ClosedCaptionsCue>(blocks.size)

        for (block in blocks) {
            val lines = block
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("WEBVTT") }

            if (lines.size < 2) continue

            val timingParts = lines.first().split(" --> ")
            if (timingParts.size != 2) continue

            val start = parseTimestamp(timingParts[0]) ?: continue
            val end = parseTimestamp(timingParts[1]) ?: continue
            if (end < start) continue

            val text = lines.drop(1).joinToString("\n")
            parsed.add(ClosedCaptionsCue(start, end, text))
        }

        parsed.sortBy { it.startMs }
        cues = parsed
    }

    /**
     * Clears all currently loaded cues. Safe to call at any time.
     */
    fun clear() {
        cues = emptyList()
    }

    /**
     * Retrieves the caption text active at the specified playback position.
     *
     * @param positionMs Playback position in milliseconds.
     * @return Caption text or null if no cue is active.
     */
    fun textAt(positionMs: Long): String? {
        val local = cues
        if (local.isEmpty()) return null

        var low = 0
        var high = local.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (local[mid].startMs > positionMs) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        val idx = low - 1
        if (idx < 0) return null
        val cue = local[idx]
        // End timestamp is EXCLUSIVE (active if start <= t < end)
        return if (positionMs < cue.endMs) cue.text else null
    }

    /**
     * Converts a WebVTT timestamp string to milliseconds.
     *
     * Supported formats:
     *  - HH:MM:SS.mmm
     *  - MM:SS.mmm
     *  - HH:MM:SS (no millis)
     *  - MM:SS (no millis)
     * Comma millisecond separators are normalized to '.' first.
     *
     * @param rawTs Raw timestamp segment.
     * @return Milliseconds from start or null if parsing fails.
     */
    private fun parseTimestamp(rawTs: String): Long? {
        val ts = rawTs.trim().replace(',', '.')
        // Formats: HH:MM:SS.mmm or MM:SS.mmm
        val parts = ts.split(":")
        if (parts.size !in 2..3) return null

        return try {
            val (h, m, sFrac) = when (parts.size) {
                3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2])
                2 -> Triple(0L, parts[0].toLong(), parts[1])
                else -> return null
            }
            val secParts = sFrac.split(".")
            val seconds = secParts[0].toLong()
            val millis = when (secParts.size) {
                2 -> (secParts[1].padEnd(3, '0').take(3)).toLong()
                1 -> 0L
                else -> return null
            }
            (h * 3600_000L) + (m * 60_000L) + (seconds * 1000L) + millis
        } catch (_: NumberFormatException) {
            null
        }
    }
}
