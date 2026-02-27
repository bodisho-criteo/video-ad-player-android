package com.iab.omid.sampleapp.manager

import com.iab.omid.sampleapp.manager.vast.VastAd
import com.iab.omid.sampleapp.manager.vast.VastManager
import com.iab.omid.sampleapp.util.CriteoLogger
import com.iab.omid.sampleapp.util.CriteoLogger.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Orchestrates downloading *and* parsing of a VAST XML document.
 *
 * Responsibilities:
 *  - Execute an HTTP GET (via the provided [OkHttpClient]) on a caller‑supplied VAST tag URL.
 *  - Enforce a few basic invariants (successful HTTP status & non‑blank body).
 *  - Hand the raw XML string to [com.iab.omid.sampleapp.manager.vast.VastManager] for parsing, off the main thread.
 *  - Expose a suspend API that returns a [Result] so callers can pattern‑match success/failure.
 *  - Emit structured log statements for diagnostics (debug, network, error, info levels).
 *
 * Threading:
 *  - Network + parsing work is confined to [Dispatchers.IO]. The public suspend function can be
 *  invoked from any coroutine context (including Main) without blocking it.
 *
 * Error model:
 *  - Network failures, non‑2xx HTTP codes, blank bodies and parsing failures are captured in the
 *  returned [Result] (as [Result.failure]).
 */
class NetworkManager(
    private val httpClient: OkHttpClient,
    private val vastManager: VastManager
) {

    /**
     * Fetches a remote VAST XML document and parses it into a [com.iab.omid.sampleapp.manager.vast.VastAd].
     *
     * @param url Fully qualified remote URL pointing to a VAST tag.
     * @return [Result] containing a parsed [com.iab.omid.sampleapp.manager.vast.VastAd] on success; on failure the [Result] wraps the
     *         originating exception. Common failure causes include:
     *         - Network I/O problems (timeouts, DNS, connectivity)
     *         - Non-success HTTP status codes (anything outside 200..299)
     *         - Empty / blank response body
     *         - XML parsing / validation errors raised by [VastManager]
     *
     * Cancellation: If the calling coroutine is cancelled the in‑flight OkHttp [Call] is also
     * cancelled and the function throws [CancellationException] (not wrapped in [Result]).
     */
    suspend fun fetchAndParseVast(url: String): Result<VastAd> = runCatching {
        CriteoLogger.debug("Fetching VAST XML from: $url", Category.NETWORK)

        // Shift blocking network + parsing work onto IO dispatcher.
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            // Await the OkHttp call in a cancellable way (see extension below).
            val response = httpClient.newCall(request).await()
            val responseBody = response.use { resp ->
                // Fail fast for non-successful status codes; the exception is captured by runCatching.
                require(resp.isSuccessful) { "VAST fetch failed with HTTP ${resp.code}" }

                // Extract body as a String, then validate it is non-blank.
                resp.body.string().also {
                    require(it.isNotBlank()) { "VAST response body is blank" }
                }
            }

            // Delegate parsing to VastManager (may itself perform additional background work).
            vastManager.parseVast(responseBody)
        }
    }.onFailure { error ->
        CriteoLogger.error("VAST fetch failed: ${error.message}", Category.NETWORK)
    }.onSuccess { vastAd ->
        CriteoLogger.info("VAST fetch succeeded: $vastAd", Category.NETWORK)
    }
}

/**
 * Suspends until this OkHttp [Call] completes, returning its [Response] or throwing on failure.
 *
 * Features:
 *  - Fully integrates with structured coroutine cancellation: if the caller is cancelled the
 *    underlying HTTP call is cancelled too.
 *  - Exceptions from OkHttp (e.g. timeouts, connectivity) are delivered as normal failures.
 *  - No retries / backoff are applied here (keep this layer minimal; add higher-level policies
 *    above if needed).
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isCancelled) return // Cancellation wins; ignore late failure callback.
            cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    // If the coroutine is cancelled, propagate that to OkHttp.
    cont.invokeOnCancellation { try { cancel() } catch (_: Exception) {} }
}
