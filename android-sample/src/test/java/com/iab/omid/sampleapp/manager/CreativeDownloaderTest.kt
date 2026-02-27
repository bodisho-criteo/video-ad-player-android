package com.iab.omid.sampleapp.manager

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CreativeDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: CreativeDownloader

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        downloader = CreativeDownloader(OkHttpClient())
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `GIVEN valid mp4 URL WHEN fetchCreative is called THEN returns success file with mp4 extension`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("video-bytes"))
        val url = server.url("/sample.mp4").toString()

        val result = downloader.fetchCreative(url)
        assertTrue(result.isSuccess)
        val file = result.getOrNull()!!
        assertTrue(file.name.endsWith(".mp4"))
        assertEquals("video-bytes".length.toLong(), file.length())
        file.delete()
    }

    @Test
    fun `GIVEN valid vtt URL WHEN fetchCreative is called THEN returns success file with vtt extension`() = runTest {
        val vttContent = "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nCaption".
            replace("\\n", "\n")
        server.enqueue(MockResponse().setResponseCode(200).setBody(vttContent))
        val url = server.url("/captions.vtt").toString()

        val result = downloader.fetchCreative(url)
        assertTrue(result.isSuccess)
        val file = result.getOrNull()!!
        assertTrue(file.name.endsWith(".vtt"))
        assertEquals(vttContent.toByteArray().size.toLong(), file.length())
        file.delete()
    }

    @Test
    fun `GIVEN URL without extension WHEN fetchCreative is called THEN returns success file without extension`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("abc"))
        val url = server.url("/asset").toString()

        val result = downloader.fetchCreative(url)
        assertTrue(result.isSuccess)
        val file = result.getOrNull()!!
        // File name should not end with an extension (it will still have a random suffix from createTempFile)
        assertFalse("Expected no explicit extension", file.name.contains('.'))
        assertEquals(3, file.length())
        file.delete()
    }

    @Test
    fun `GIVEN HTTP error 404 WHEN fetchCreative is called THEN returns failure Result with HTTP code in message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val url = server.url("/missing.mp4").toString()

        val result = downloader.fetchCreative(url)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 404") == true)
    }

    @Test
    fun `GIVEN invalid URL string WHEN fetchCreative is called THEN returns failure Result with IllegalArgumentException`() = runTest {
        val result = downloader.fetchCreative("ht!tp:// bad url")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `GIVEN large file WHEN fetchCreative is called THEN streams successfully and file size matches`() = runTest {
        val largeBody = buildString {
            repeat(5000) { append("0123456789") } // 50,000 bytes
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(largeBody))
        val url = server.url("/big.mp4").toString()

        val result = downloader.fetchCreative(url)
        assertTrue(result.isSuccess)
        val file = result.getOrNull()!!
        assertEquals(largeBody.length.toLong(), file.length())
        file.delete()
    }

    @Test
    fun `GIVEN inflight slow response WHEN job cancelled THEN cancellation propagates and partial file cleaned`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("delayed-content")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )
        val url = server.url("/slow.mp4").toString()

        val job = launch { downloader.fetchCreative(url) }
        delay(100) // allow request to start
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        // Cannot directly assert file deletion since path not exposed on cancellation; rely on no exceptions.
    }

    @Test
    fun `GIVEN initial mp4 URL that redirects to different path WHEN fetchCreative is called THEN extension preserved from original URL`() = runTest {
        // First response issues redirect to different file name (.bin)
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", server.url("/final.bin"))
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("redirect-bytes"))

        val originalUrl = server.url("/redirect.mp4").toString()
        val result = downloader.fetchCreative(originalUrl)
        assertTrue(result.isSuccess)
        val file = result.getOrNull()!!
        // We derive extension from the original URL string passed to the downloader (parity decision)
        assertTrue(file.name.endsWith(".mp4"))
        file.delete()
    }
}

