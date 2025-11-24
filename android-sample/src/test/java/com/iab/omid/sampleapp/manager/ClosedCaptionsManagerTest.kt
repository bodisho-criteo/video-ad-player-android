package com.iab.omid.sampleapp.manager

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class ClosedCaptionsManagerTest {

    // Shared transcript VTT used by all tests.
    val vtt = """WEBVTT

00:00:00.039 --> 00:00:00.380
Thanks you

00:00:02.910 --> 00:00:03.220
Wait,

00:00:03.589 --> 00:00:04.480
before you click that,

00:00:04.550 --> 00:00:06.380
I'm you from 20 years in the future.

00:00:06.949 --> 00:00:07.260
Wait.

00:00:09.670 --> 00:00:10.189
Oh.

00:00:11.239 --> 00:00:12.390
Don't  listen to her.

00:00:12.960 --> 00:00:15.310
I'm you from even further in the future.

00:00:21.299 --> 00:00:21.370
Me too. I had a message.

00:00:21.379 --> 00:00:23.079
Everyone will try to tell you what the future holds.

00:00:24.159 --> 00:00:27.389
Criteo is creating an open internet where you can choose what's best for you.

00:00:28.139 --> 00:00:29.479
And future you.""".trimIndent()

    private lateinit var manager: ClosedCaptionsManager

    @Before
    fun setUp() {
        manager = ClosedCaptionsManager()
    }

    @Test
    fun `GIVEN valid vtt file WHEN loaded into manager THEN textAt returns captions with exclusive end boundaries`() {
        manager.load(ByteArrayInputStream(vtt.toByteArray()))

        // Before first cue start
        assertNull(manager.textAt(0))

        // First cue active range
        assertEquals("Thanks you", manager.textAt(39))
        assertEquals("Thanks you", manager.textAt(379))
        assertNull(manager.textAt(380)) // exclusive end

        // Gap until second cue (2910 start)
        assertNull(manager.textAt(1000))
        assertEquals("Wait,", manager.textAt(2910))
        assertEquals("Wait,", manager.textAt(3219))
        assertNull(manager.textAt(3220)) // exclusive end

        // Third cue
        assertEquals("before you click that,", manager.textAt(3589))
        assertEquals("before you click that,", manager.textAt(4479))
        assertNull(manager.textAt(4480))

        // Fourth cue
        assertEquals("I'm you from 20 years in the future.", manager.textAt(4550))
        assertEquals("I'm you from 20 years in the future.", manager.textAt(6379))
        assertNull(manager.textAt(6380))

        // Mid later cue
        assertEquals("Don't  listen to her.", manager.textAt(11500))

        // Last cue range
        assertEquals("And future you.", manager.textAt(28139))
        assertEquals("And future you.", manager.textAt(29478))
        assertNull(manager.textAt(29479)) // exclusive end of final cue
    }

    @Test
    fun `GIVEN valid vtt file WHEN loaded into manager THEN textAt returns correct captions or null`() {
        manager.load(ByteArrayInputStream(vtt.toByteArray()))
        // Random sampling
        assertNull(manager.textAt(500)) // gap
        assertEquals("before you click that,", manager.textAt(3589)) // inside third cue
        assertEquals("I'm you from 20 years in the future.", manager.textAt(5000))
        assertEquals("Wait.", manager.textAt(7000)) // 6949-7260; 7000 in range
        assertNull(manager.textAt(8000)) // gap before 9670
        assertEquals("Oh.", manager.textAt(10000))
        assertEquals("I'm you from even further in the future.", manager.textAt(15000)) // 12960-15310 range
        assertNull(manager.textAt(16000)) // gap before 21299
        assertEquals("Me too. I had a message.", manager.textAt(21299))
        assertEquals("Everyone will try to tell you what the future holds.", manager.textAt(22000))
        assertEquals("Criteo is creating an open internet where you can choose what's best for you.", manager.textAt(25000))
    }

    @Test
    fun `GIVEN loaded captions WHEN clear invoked THEN no captions returned`() {
        manager.load(ByteArrayInputStream(vtt.toByteArray()))
        assertEquals("Thanks you", manager.textAt(39))
        manager.clear()
        assertNull(manager.textAt(39))
    }

    @Test
    fun `GIVEN empty manager WHEN querying THEN returns null`() {
        assertNull(manager.textAt(39))
    }
}
