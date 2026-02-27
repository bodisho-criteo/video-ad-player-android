package com.iab.omid.sampleapp.manager.omid

import android.content.Context
import android.view.View

/**
 * Common interface for OMID session management.
 *
 * This interface defines the contract for OMID session interactors, allowing
 * both the real implementation and stub to be used interchangeably.
 */
interface IOMIDSessionInteractor {

    // Session Lifecycle
    fun startSession()
    fun stopSession()

    // Ad Events
    fun fireAdLoaded()
    fun fireImpression()

    // Media Events
    fun fireStart(durationMs: Long, volume: Float)
    fun fireFirstQuartile()
    fun fireMidpoint()
    fun fireThirdQuartile()
    fun fireComplete()
    fun firePause()
    fun fireResume()
    fun fireVolumeChange(volume: Float)
    fun fireBufferStart()
    fun fireBufferFinish()
    fun fireSkipped()
    fun fireClickInteraction()

    // Friendly Obstructions
    fun addMediaControlsObstruction(element: View)
}

/**
 * Factory for creating OMID session interactor instances.
 *
 * Automatically chooses between the real OMIDSessionInteractor and
 * OMIDSessionInteractorStub based on OMID SDK availability.
 *
 * To enable OMID functionality refer to the official documentation
 * https://developers.criteo.com/retailer-integration/update/docs/video-player-implementation-app-android
 */
/**
object OMIDSessionInteractorFactory {

    /**
     * Creates an OMID session interactor instance.
     *
     * @param context Android context
     * @param adView The main ad view for viewability measurement
     * @param vendorKey Vendor identifier for verification scripts
     * @param verificationScriptURL URL of the verification script
     * @param verificationParameters Parameters to pass to the verification script
     *
     * @return IOMIDSessionInteractor instance (real if OMSDK is activated or stub otherwise)
     */
    fun create(
        context: Context,
        adView: View,
        vendorKey: String,
        verificationScriptURL: String,
        verificationParameters: String
    ): IOMIDSessionInteractor = if (OMIDSessionInteractor.isOMSDKActive()) {
        OMIDSessionInteractor(context, adView, vendorKey, verificationScriptURL, verificationParameters)
    } else {
        OMIDSessionInteractorStub()
    }

    /**
     *  @return true if activation successful
     */
    fun activateOMSDK(context: Context): Boolean = try {
        OMIDSessionInteractor.activateOMSDK(context)
    } catch (_: Exception) {
        // OMID SDK not available, will use stub
        false
    }
}
*/


object OMIDSessionInteractorFactory {
    fun create(
        context: Context,
        adView: View,
        vendorKey: String,
        verificationScriptURL: String,
        verificationParameters: String
    ): IOMIDSessionInteractor = OMIDSessionInteractorStub()

    fun activateOMSDK(context: Context): Boolean = false
}
