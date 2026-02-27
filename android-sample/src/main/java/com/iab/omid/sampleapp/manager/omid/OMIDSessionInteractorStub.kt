package com.iab.omid.sampleapp.manager.omid

import android.view.View
import com.iab.omid.sampleapp.util.CriteoLogger

/**
 * No-op stub implementation of [IOMIDSessionInteractor] for testing and development.
 *
 * This class provides the same API as [OMIDSessionInteractor] but performs no actual
 * OMID operations. All method calls are logged via [CriteoLogger] for debugging.
 */
class OMIDSessionInteractorStub : IOMIDSessionInteractor {

    init {
        log("initialized")
    }

    override fun startSession() {
        log("startSession")
    }

    override fun stopSession() {
        log("stopSession")
    }

    override fun fireAdLoaded() {
        log("fireAdLoaded")
    }

    override fun fireImpression() {
        log("fireImpression")
    }

    override fun fireStart(durationMs: Long, volume: Float) {
        log("fireStart (duration=${durationMs}ms, volume=$volume)")
    }

    override fun fireFirstQuartile() {
        log("fireFirstQuartile")
    }

    override fun fireMidpoint() {
        log("fireMidpoint")
    }

    override fun fireThirdQuartile() {
        log("fireThirdQuartile")
    }

    override fun fireComplete() {
        log("fireComplete")
    }

    override fun firePause() {
        log("firePause")
    }

    override fun fireResume() {
        log("fireResume")
    }

    override fun fireVolumeChange(volume: Float) {
        log("fireVolumeChange (volume=$volume)")
    }

    override fun fireBufferStart() {
        log("fireBufferStart")
    }

    override fun fireBufferFinish() {
        log("fireBufferFinish")
    }

    override fun fireSkipped() {
        log("fireSkipped")
    }

    override fun fireClickInteraction() {
        log("fireClick")
    }

    override fun addMediaControlsObstruction(element: View) {
        log("addMediaControlsObstruction: ${element.javaClass.simpleName}")
    }

    private fun log(message: String) {
        CriteoLogger.info("[OMID-Stub] $message", CriteoLogger.Category.OMID)
    }
}
