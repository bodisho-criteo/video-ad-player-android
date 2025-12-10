package com.iab.omid.sampleapp.manager

import android.util.Log
import com.iab.omid.sampleapp.manager.vast.VastAd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Manages beacon firing with retry logic, error handling, and task cancellation.
 */
class BeaconManager(
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    /**
     * Fires a beacon to a specific URL with retry logic and proper error handling.
     */
    fun fireBeacon(url: URL, type: String) {
        scope.launch { fireBeaconWithRetry(url, type, attempt = 0) }
    }

    /**
     * Fires all impression beacons from a VAST ad.
     */
    fun fireImpressionBeacons(ad: VastAd) {
        ad.impressionUrls.forEach { url -> fireBeacon(url, "impression") }
    }

    /**
     * Fires all click tracking beacons from a VAST ad.
     */
    fun fireClickTrackingBeacons(ad: VastAd) {
        ad.clickTrackingUrls.forEach { url -> fireBeacon(url, "clickTracking") }
    }

    /**
     * Fires a beacon with retry logic and exponential backoff.
     */
    private suspend fun fireBeaconWithRetry(url: URL, type: String, attempt: Int, maxAttempts: Int = MAX_ATTEMPTS) {
        Log.d(TAG, "Firing $type beacon (attempt $attempt/$maxAttempts)")

        try {
            val responseCode = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .build()
                httpClient.newCall(request).execute().use { it.code }
            }

            if (responseCode in 200..299) {
                Log.i(TAG, "$type beacon succeeded for $url")
            } else {
                Log.w(TAG, "$type beacon returned status $responseCode for $url")
                if (shouldRetryForStatusCode(responseCode) && attempt < maxAttempts) {
                    retryAfterDelay(
                        url = url,
                        type = type,
                        attempt = attempt + 1,
                        maxAttempts = maxAttempts
                    )
                } else {
                    Log.e(TAG, "$type beacon failed permanently with status: $responseCode")
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "$type beacon cancelled: ${e.message}")
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "$type beacon failed: ${e.localizedMessage}")
            if (attempt < maxAttempts) {
                retryAfterDelay(
                    url = url,
                    type = type,
                    attempt = attempt + 1,
                    maxAttempts = maxAttempts
                )
            } else {
                Log.e(TAG, "$type beacon failed permanently after $attempt attempts")
            }
        }
    }

    /**
     * Determines if we should retry based on HTTP status code.
     */
    private fun shouldRetryForStatusCode(statusCode: Int): Boolean =
        statusCode >= 500 || statusCode == 408 || statusCode == 429

    /**
     * Waits for exponential backoff delay then retries.
     */
    private suspend fun retryAfterDelay(url: URL, type: String, attempt: Int, maxAttempts: Int) {
        val delayMs = Math.pow(2.0, attempt - 1.0).toLong() * 1000L // 1s,2s,4s
        Log.d(TAG, "Retrying $type beacon in ${delayMs / 1000.0}s...")
        delay(delayMs)
        fireBeaconWithRetry(url, type, attempt, maxAttempts)
    }

    companion object {
        private const val TAG = "BeaconManager"
        private const val USER_AGENT = "OM-Demo/1.0"
        private const val MAX_ATTEMPTS = 3
    }
}
