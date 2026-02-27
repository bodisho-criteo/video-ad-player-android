package com.iab.omid.sampleapp.manager

import com.iab.omid.sampleapp.manager.vast.VastManager
import com.iab.omid.sampleapp.util.CriteoLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var manager: NetworkManager

    // Minimal valid VAST fixture
    private val sampleVast = """
        <VAST><Ad><InLine>
          <Impression>https://example.com/imp</Impression>
          <Creatives><Creative><Linear>
            <Duration>00:00:10</Duration>
            <TrackingEvents><Tracking event='start'>https://example.com/start</Tracking></TrackingEvents>
            <MediaFiles><MediaFile width='640' height='360' type='video/mp4'>https://example.com/video.mp4</MediaFile></MediaFiles>
          </Linear></Creative></Creatives>
        </InLine></Ad></VAST>
    """.trimIndent()

    // Well-formed but semantically empty (no media/tracking)
    private val emptySemanticVast = """
        <VAST><Ad><InLine><Creatives></Creatives></InLine></Ad></VAST>
    """.trimIndent()

    // Malformed XML (will cause parse manager to return empty ad)
    private val malformedXml = "<VAST><Ad>"

    @Before
    fun setUp() {
        CriteoLogger.minimumLevel = CriteoLogger.Level.CRITICAL // disable debug logs for tests
        server = MockWebServer().apply { start() }
        manager = NetworkManager(OkHttpClient(), VastManager())
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `GIVEN valid VAST XML WHEN fetch and parse THEN returns populated VastAd`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(sampleVast))
        val result = manager.fetchAndParseVast(server.url("/vast.xml").toString())
        Assert.assertTrue(result.isSuccess)
        val ad = result.getOrNull()!!
        Assert.assertEquals("00:00:10", ad.duration)
        Assert.assertEquals(1, ad.mediaFiles.size)
        Assert.assertEquals("https://example.com/video.mp4", ad.mediaFiles.first().url.toString())
        Assert.assertEquals(1, ad.impressionUrls.size)
        Assert.assertEquals("https://example.com/imp", ad.impressionUrls.first().toString())
        Assert.assertEquals(1, ad.trackingEvents.size)
        Assert.assertNotNull(ad.videoUrl)
    }

    @Test
    fun `GIVEN HTTP error WHEN fetch and parse THEN failure Result with HTTP code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val result = manager.fetchAndParseVast(server.url("/vast.xml").toString())
        Assert.assertTrue(result.isFailure)
        Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        Assert.assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 404") == true)
    }

    @Test
    fun `GIVEN HTTP 200 blank body WHEN fetch and parse THEN failure Result blank body`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            val result = manager.fetchAndParseVast(server.url("/vast.xml").toString())
            Assert.assertTrue(result.isFailure)
            Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            Assert.assertTrue(result.exceptionOrNull()?.message?.contains("blank") == true)
        }

    @Test
    fun `GIVEN malformed XML WHEN fetch and parse THEN success with empty VastAd`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(malformedXml))
        val result = manager.fetchAndParseVast(server.url("/vast.xml").toString())
        Assert.assertTrue(
            "Malformed XML should still yield success result with empty VastAd",
            result.isSuccess
        )
        val ad = result.getOrNull()
        Assert.assertTrue(ad?.mediaFiles?.isEmpty() == true)
        Assert.assertTrue(ad?.impressionUrls?.isEmpty() == true)
        Assert.assertTrue(ad?.trackingEvents?.isEmpty() == true)
        Assert.assertNull(ad?.videoUrl)
    }

    @Test
    fun `GIVEN semantically empty VAST WHEN fetch and parse THEN success with empty VastAd`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(emptySemanticVast))
            val result = manager.fetchAndParseVast(server.url("/vast.xml").toString())
            Assert.assertTrue(result.isSuccess)
            val ad = result.getOrNull()!!
            Assert.assertTrue(ad.mediaFiles.isEmpty())
            Assert.assertTrue(ad.trackingEvents.isEmpty())
            Assert.assertNull(ad.videoUrl)
        }

    @Test
    fun `GIVEN invalid URL WHEN fetch and parse THEN failure Result illegal argument`() = runTest {
        // OkHttp will throw IllegalArgumentException for invalid URL format inside runCatching
        val result = manager.fetchAndParseVast("ht!tp://bad url")
        Assert.assertTrue(result.isFailure)
        Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `GIVEN inflight request WHEN job is cancelled THEN call is cancelled`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sampleVast)
                .setBodyDelay(2, TimeUnit.SECONDS)
        )
        val job = launch { manager.fetchAndParseVast(server.url("/vast.xml").toString()) }
        delay(100) // allow request to start
        job.cancelAndJoin()
        Assert.assertTrue(job.isCancelled)
    }
}