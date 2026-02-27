package com.iab.omid.sampleapp.manager

import com.iab.omid.sampleapp.util.CriteoLogger
import com.iab.omid.sampleapp.util.CriteoLogger.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Downloads a remote creative (video or caption) into a temporary local file while preserving
 * the original URL's file extension (if any).
 */
class CreativeDownloader(private val httpClient: OkHttpClient) {

    /**
     * Downloads the creative at [remoteUrl] into a temp file.
     * @param remoteUrl Fully-qualified HTTP/HTTPS URL.
     * @return Success with the local [File]; failure with the originating exception.
     * Cancellation propagates (not wrapped) if the coroutine is cancelled mid-flight.
     */
    suspend fun fetchCreative(remoteUrl: String): Result<File> {
        var tempFile: File? = null
        val start = System.nanoTime()
        val ext = extractExtension(remoteUrl)

        CriteoLogger.info("Downloading $ext creative", Category.NETWORK)

        return try {
            val file = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(remoteUrl).get().build()
                val call = httpClient.newCall(request)
                // Cancel underlying HTTP call if the coroutine is cancelled.
                coroutineContext.job.invokeOnCompletion { cause -> if (cause is CancellationException) call.cancel() }
                val response = call.execute()
                response.use { resp ->
                    require(resp.isSuccessful) { "Creative download failed HTTP ${resp.code}" }
                    val body = resp.body

                    // Prepare destination temp file (extension preserved if present).
                    tempFile = File.createTempFile(
                        /* prefix = */ "creative_",
                        /* suffix = */ if (ext.isNotEmpty()) ".${ext.lowercase()}" else ""
                    )

                    // Perform streaming copy.
                    body.byteStream().use { input ->
                        tempFile!!.outputStream().use { output -> copyStream(input, output) }
                    }

                    val elapsed = (System.nanoTime() - start) / 1_000_000_000.0

                    CriteoLogger.network("$ext creative downloaded", url = java.net.URL(remoteUrl), statusCode = resp.code)
                    CriteoLogger.debug("$ext download completed in ${"%.2f".format(elapsed)}s, size: ${tempFile.length()} bytes", Category.NETWORK)
                    CriteoLogger.debug("$ext creative saved to: ${tempFile.name}", Category.NETWORK)
                    tempFile
                }
            }
            Result.success(file)
        } catch (ce: CancellationException) {
            // Clean up partial file then rethrow to propagate cancellation.
            tempFile?.safeDelete()
            throw ce
        } catch (e: Exception) {
            val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
            CriteoLogger.error("$ext creative download failed after ${"%.2f".format(elapsed)}s: ${e.localizedMessage}", Category.NETWORK)
            tempFile?.safeDelete()
            Result.failure(e)
        }
    }

    private fun extractExtension(url: String): String {
        // Strip query/fragment then pull last path segment's extension.
        val clean = url.substringBefore('?').substringBefore('#')
        val lastSegment = clean.substringAfterLast('/')
        val ext = lastSegment.substringAfterLast('.', "")
        return if (ext.contains('/')) "" else ext // Safety: malformed path.
    }

    private fun copyStream(input: InputStream, output: java.io.OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = try { input.read(buffer) } catch (io: IOException) { throw io }
            if (read == -1) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun File.safeDelete() = runCatching { delete() }
}
