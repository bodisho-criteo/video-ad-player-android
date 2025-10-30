package com.iab.omid.sampleapp.manager.vast

import org.junit.Assert.*
import org.junit.Test

class VastManagerTest {

    private val vastManager = VastManager()

    @Test
    fun parseVast_fullSample_extractsAllFields() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <VAST xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema" version="4.2">
                <Ad id="609475258823917568">
                    <InLine>
                        <AdSystem>Criteo</AdSystem>
                        <AdTitle>OnsiteVideo</AdTitle>
                        <Impression><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/impression]]></Impression>
                        <Error><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/error]]></Error>
                        <Creatives>
                            <Creative id="35934">
                                <Linear>
                                    <Duration>00:00:17</Duration>
                                    <TrackingEvents>
                                        <Tracking event="start"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/start]]></Tracking>
                                        <Tracking event="firstQuartile"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/firstQuartile]]></Tracking>
                                        <Tracking event="midpoint"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/midpoint]]></Tracking>
                                        <Tracking event="thirdQuartile"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/thirdQuartile]]></Tracking>
                                        <Tracking event="complete"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/complete]]></Tracking>
                                        <Tracking event="mute"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/mute]]></Tracking>
                                        <Tracking event="unmute"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/unmute]]></Tracking>
                                        <Tracking event="pause"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/pause]]></Tracking>
                                        <Tracking event="resume"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/resume]]></Tracking>
                                    </TrackingEvents>
                                    <MediaFiles>
                                        <MediaFile id="id" delivery="progressive" width="640" height="360" type="video/mp4" scalable="true" maintainAspectRatio="true"><![CDATA[https://raw.githubusercontent.com/criteo/interview-ios/refs/heads/main/server/criteo.mp4]]></MediaFile>
                                        <ClosedCaptionFiles>
                                            <ClosedCaptionFile type="text/vtt" language="en"><![CDATA[https://raw.githubusercontent.com/criteo/interview-ios/refs/heads/main/server/criteo.vtt]]></ClosedCaptionFile>
                                        </ClosedCaptionFiles>
                                    </MediaFiles>
                                    <VideoClicks>
                                        <ClickTracking><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/click]]></ClickTracking>
                                    </VideoClicks>
                                </Linear>
                            </Creative>
                        </Creatives>
                        <AdVerifications>
                            <Verification vendor="criteo.com-omid">
                                <JavaScriptResource apiFramework="omid" browserOptional="true"><![CDATA[https://static.criteo.net/banners/js/omidjs/stable/omid-validation-verification-script-for-retail-media.js]]></JavaScriptResource>
                                <VerificationParameters><![CDATA[{"beacons":{
                                    "omidTrackView":              "https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/omidTrackView",
                                    "start":                      "https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/start",
                                    "firstQuartile":              "https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/firstQuartile",
                                    "midpoint":                   "https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/midpoint",
                                    "thirdQuartile":              "https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/thirdQuartile",
                                    "complete":                   "https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/complete",
                                    "twoSecondsFiftyPercentView": "https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/twoSecondsFiftyPercentView"
                                }}]]>
                            </VerificationParameters>
                                <TrackingEvents>
                                    <Tracking event="verificationNotExecuted"><![CDATA[https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/verificationNotExecuted]]></Tracking>
                                </TrackingEvents>
                            </Verification>
                        </AdVerifications>
                    </InLine>
                </Ad>
            </VAST>
        """.trimIndent()

        val ad = vastManager.parseVast(xml)

        // Duration
        assertEquals("00:00:17", ad.duration)
        // Media file
        assertEquals(1, ad.mediaFiles.size)
        ad.mediaFiles.first().let { media ->
            assertEquals(640, media.width)
            assertEquals(360, media.height)
            assertEquals("video/mp4", media.type)
            assertEquals("https://raw.githubusercontent.com/criteo/interview-ios/refs/heads/main/server/criteo.mp4", media.url.toString())
        }
        // Impression
        assertEquals(1, ad.impressionUrls.size)
        assertEquals("https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/impression", ad.impressionUrls.first().toString())
        // Error
        assertEquals(1, ad.errorUrls.size)
        assertEquals("https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/error", ad.errorUrls.first().toString())
        // Tracking events
        val expectedEvents = listOf(
            "start", "firstQuartile", "midpoint", "thirdQuartile", "complete", "mute", "unmute", "pause", "resume"
        )
        assertTrue(ad.trackingEvents.keys.containsAll(expectedEvents))
        expectedEvents.forEach { ev ->
            assertTrue("Missing URL for $ev", ad.trackingEvents[ev]?.toString()?.contains("/tracking/$ev") == true)
        }
        // Click tracking
        assertEquals(1, ad.clickTrackingUrls.size)
        assertEquals("https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/tracking/click", ad.clickTrackingUrls.first().toString())
        // No ClickThrough in this sample
        assertNull(ad.clickThroughUrl)
        // Closed caption
        assertEquals("https://raw.githubusercontent.com/criteo/interview-ios/refs/heads/main/server/criteo.vtt", ad.closedCaptionUrl?.toString())
        // Verification script
        assertEquals("https://static.criteo.net/banners/js/omidjs/stable/omid-validation-verification-script-for-retail-media.js", ad.verificationScriptUrl?.toString())
        // Verification parameters contain key substring
        assertNotNull(ad.verificationParameters)
        assertTrue(ad.verificationParameters!!.contains("twoSecondsFiftyPercentView"))
        // Verification tracking event
        assertEquals("https://httpdump.app/dumps/59fe4255-b0c3-45ba-8b32-dbf71c8e0226/measurement/verificationNotExecuted", ad.verificationTracking["verificationNotExecuted"]?.toString())
        // Vendor key
        assertEquals("criteo.com-omid", ad.vendorKey)
    }

    @Test
    fun parseVast_minimal_returnsEmptyModel() {
        val xml = """
            <VAST>
                <Ad>
                    <InLine>
                        <Creatives>
                        </Creatives>
                    </InLine>
                </Ad>
            </VAST>
        """.trimIndent()

        val ad = vastManager.parseVast(xml)

        assertNull(ad.videoUrl)
        assertTrue(ad.mediaFiles.isEmpty())
        assertNull(ad.duration)
        assertTrue(ad.impressionUrls.isEmpty())
        assertTrue(ad.errorUrls.isEmpty())
        assertTrue(ad.trackingEvents.isEmpty())
        assertTrue(ad.clickTrackingUrls.isEmpty())
        assertNull(ad.clickThroughUrl)
        assertNull(ad.closedCaptionUrl)
        assertNull(ad.verificationScriptUrl)
        assertNull(ad.verificationParameters)
        assertTrue(ad.verificationTracking.isEmpty())
        assertNull(ad.vendorKey)
    }
}
