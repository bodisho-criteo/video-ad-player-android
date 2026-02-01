package com.iab.omid.sampleapp.manager.omid

import android.content.Context
import android.view.View
import com.iab.omid.library.criteo.Omid
import com.iab.omid.library.criteo.adsession.AdEvents
import com.iab.omid.library.criteo.adsession.AdSession
import com.iab.omid.library.criteo.adsession.AdSessionConfiguration
import com.iab.omid.library.criteo.adsession.AdSessionContext
import com.iab.omid.library.criteo.adsession.CreativeType
import com.iab.omid.library.criteo.adsession.FriendlyObstructionPurpose
import com.iab.omid.library.criteo.adsession.ImpressionType
import com.iab.omid.library.criteo.adsession.Owner
import com.iab.omid.library.criteo.adsession.Partner
import com.iab.omid.library.criteo.adsession.VerificationScriptResource
import com.iab.omid.library.criteo.adsession.media.InteractionType
import com.iab.omid.library.criteo.adsession.media.MediaEvents
import com.iab.omid.sampleapp.BuildConfig
import com.iab.omid.sampleapp.R
import com.iab.omid.sampleapp.util.CriteoLogger
import com.iab.omid.sampleapp.util.CriteoLogger.Category
import java.io.IOException
import java.net.URL

/**
 * Utility wrapper around the OMID SDK for native video ad measurement.
 *
 * This class encapsulates OMID session management including:
 * - Session creation and configuration
 * - Ad events (loaded, impression)
 * - Media events (start, quartiles, pause, resume, volume, complete)
 * - Friendly obstruction management
 *
 * @param context Android context for OMID SDK initialization
 * @param adView The main ad view for viewability measurement
 * @param vendorKey Vendor identifier for verification scripts
 * @param verificationScriptURL URL of the verification script
 * @param verificationParameters Parameters to pass to the verification script
 */
class OMIDSessionInteractor(
    private val context: Context,
    private val adView: View,
    private val vendorKey: String,
    private val verificationScriptURL: String,
    private val verificationParameters: String
) : IOMIDSessionInteractor {

    private var adSession: AdSession? = null

    private var adEvents: AdEvents? = null
        get() {
            if (field == null) {
                CriteoLogger.warning("AdEvents not instantiated, session may not be started", Category.OMID)
            }
            return field
        }

    private var mediaEvents: MediaEvents? = null
        get() {
            if (field == null) {
                CriteoLogger.warning("MediaEvents not instantiated, session may not be started", Category.OMID)
            }
            return field
        }

    init {
        // Ensure OMID has been activated
        if (!Omid.isActive()) {
            CriteoLogger.error("OMID is not active. Call OMIDSessionInteractor.activateOMSDK() first.", Category.OMID)
        } else {
            try {
                // Create ad session configuration for native video
                val adSessionConfiguration = AdSessionConfiguration.createAdSessionConfiguration(
                    CreativeType.VIDEO,
                    ImpressionType.VIEWABLE,
                    Owner.NATIVE,
                    Owner.NATIVE,
                    false // isolateVerificationScripts
                )

                // Create partner info
                val partner = Partner.createPartner(
                    BuildConfig.PARTNER_NAME,
                    BuildConfig.VERSION_NAME
                )

                // Load OMID JS
                val omidJs = getOmidJs(context)

                // Create verification script resources
                val verificationScriptResources = try {
                    listOf(
                        VerificationScriptResource.createVerificationScriptResourceWithParameters(
                            vendorKey,
                            URL(verificationScriptURL),
                            verificationParameters
                        )
                    )
                } catch (e: Exception) {
                    CriteoLogger.error("Unable to create verification script resource: ${e.localizedMessage}", Category.OMID)
                    emptyList()
                }

                // Create ad session context
                val adSessionContext = AdSessionContext.createNativeAdSessionContext(
                    partner,
                    omidJs,
                    verificationScriptResources,
                    null, // contentUrl
                    null  // customReferenceIdentifier
                )

                // Create ad session
                adSession = AdSession.createAdSession(adSessionConfiguration, adSessionContext)

                // Register the main ad view
                adSession?.registerAdView(adView)

                CriteoLogger.info("OMID AdSession created successfully", Category.OMID)
            } catch (e: Exception) {
                CriteoLogger.error("Unable to create OMID AdSession: ${e.localizedMessage}", Category.OMID)
            }
        }
    }

    /**
     * Starts the OMID session. Must be called before firing any events.
     * Creates ad events and media events publishers.
     */
    override fun startSession() {
        val session = adSession ?: run {
            CriteoLogger.error("Cannot start session: AdSession is null", Category.OMID)
            return
        }

        CriteoLogger.info("Starting OMID session", Category.OMID)

        // Create AdEvents publisher
        try {
            adEvents = AdEvents.createAdEvents(session)
        } catch (e: Exception) {
            CriteoLogger.error("Unable to create AdEvents: ${e.localizedMessage}", Category.OMID)
        }

        // Create MediaEvents publisher
        try {
            mediaEvents = MediaEvents.createMediaEvents(session)
        } catch (e: Exception) {
            CriteoLogger.error("Unable to create MediaEvents: ${e.localizedMessage}", Category.OMID)
        }

        session.start()
    }

    /**
     * Stops the OMID session. Call when ad playback completes or is terminated.
     */
    override fun stopSession() {
        CriteoLogger.info("Stopping OMID session", Category.OMID)

        adSession?.finish()
        adSession = null
        adEvents = null
        mediaEvents = null
    }

    /**
     * Fires the ad loaded event with default VAST properties for non-skippable media.
     */
    override fun fireAdLoaded() {
        CriteoLogger.info("Firing OMID ad loaded", Category.OMID)
        try {
            adEvents?.loaded()
        } catch (e: Exception) {
            CriteoLogger.error("OMID load error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the impression event. Should be called when the first frame of video plays.
     */
    override fun fireImpression() {
        CriteoLogger.info("Firing OMID impression", Category.OMID)
        try {
            adEvents?.impressionOccurred()
        } catch (e: Exception) {
            CriteoLogger.error("OMID impression error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the start event. Should be called when video playback begins.
     *
     * @param durationMs Duration of the ad in milliseconds
     * @param volume Current player volume (0.0 to 1.0)
     */
    override fun fireStart(durationMs: Long, volume: Float) {
        CriteoLogger.info("Firing OMID start (duration=${durationMs}ms, volume=$volume)", Category.OMID)
        try {
            val durationSeconds = durationMs / 1000f // OMID expects duration in seconds as Float
            mediaEvents?.start(durationSeconds, volume)
        } catch (e: Exception) {
            CriteoLogger.error("OMID start error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the first quartile event (25% progress).
     */
    override fun fireFirstQuartile() {
        CriteoLogger.info("Firing OMID first quartile", Category.OMID)
        try {
            mediaEvents?.firstQuartile()
        } catch (e: Exception) {
            CriteoLogger.error("OMID first quartile error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the midpoint event (50% progress).
     */
    override fun fireMidpoint() {
        CriteoLogger.info("Firing OMID midpoint", Category.OMID)
        try {
            mediaEvents?.midpoint()
        } catch (e: Exception) {
            CriteoLogger.error("OMID midpoint error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the third quartile event (75% progress).
     */
    override fun fireThirdQuartile() {
        CriteoLogger.info("Firing OMID third quartile", Category.OMID)
        try {
            mediaEvents?.thirdQuartile()
        } catch (e: Exception) {
            CriteoLogger.error("OMID third quartile error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the complete event (100% progress).
     */
    override fun fireComplete() {
        CriteoLogger.info("Firing OMID complete", Category.OMID)
        try {
            mediaEvents?.complete()
        } catch (e: Exception) {
            CriteoLogger.error("OMID complete error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the pause event. Should be called when user pauses playback.
     */
    override fun firePause() {
        CriteoLogger.info("Firing OMID pause", Category.OMID)
        try {
            mediaEvents?.pause()
        } catch (e: Exception) {
            CriteoLogger.error("OMID pause error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the resume event. Should be called when user resumes playback.
     */
    override fun fireResume() {
        CriteoLogger.info("Firing OMID resume", Category.OMID)
        try {
            mediaEvents?.resume()
        } catch (e: Exception) {
            CriteoLogger.error("OMID resume error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the volume change event. Should be called when player volume changes.
     *
     * @param volume New player volume (0.0 to 1.0)
     */
    override fun fireVolumeChange(volume: Float) {
        CriteoLogger.info("Firing OMID volume change (volume=$volume)", Category.OMID)
        try {
            mediaEvents?.volumeChange(volume)
        } catch (e: Exception) {
            CriteoLogger.error("OMID volume change error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the buffer start event. Should be called when playback pauses due to buffering.
     */
    override fun fireBufferStart() {
        CriteoLogger.info("Firing OMID buffer start", Category.OMID)
        try {
            mediaEvents?.bufferStart()
        } catch (e: Exception) {
            CriteoLogger.error("OMID buffer start error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the buffer finish event. Should be called when playback resumes after buffering.
     */
    override fun fireBufferFinish() {
        CriteoLogger.info("Firing OMID buffer finish", Category.OMID)
        try {
            mediaEvents?.bufferFinish()
        } catch (e: Exception) {
            CriteoLogger.error("OMID buffer finish error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the skipped event. Should be called when ad playback is skipped.
     */
    override fun fireSkipped() {
        CriteoLogger.info("Firing OMID skipped", Category.OMID)
        try {
            mediaEvents?.skipped()
        } catch (e: Exception) {
            CriteoLogger.error("OMID skipped error: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires the click interaction event. Should be called when user clicks the ad.
     */
    override fun fireClickInteraction() {
        fireAdUserInteraction(InteractionType.CLICK)
    }

    /**
     * Adds a view as a friendly obstruction for media controls.
     *
     * @param element The view to add as an obstruction (e.g., play/pause button)
     */
    override fun addMediaControlsObstruction(element: View) {
        CriteoLogger.debug("Adding media controls obstruction", Category.OMID)
        try {
            adSession?.addFriendlyObstruction(
                element,
                FriendlyObstructionPurpose.VIDEO_CONTROLS,
                "Media Controls over video"
            )
        } catch (e: Exception) {
            CriteoLogger.error("Unable to add friendly obstruction: ${e.localizedMessage}", Category.OMID)
        }
    }

    /**
     * Fires ad user interaction event (e.g., click).
     *
     * @param interactionType Type of interaction
     */
    private fun fireAdUserInteraction(interactionType: InteractionType) {
        CriteoLogger.info("Firing OMID ad user interaction: $interactionType", Category.OMID)
        try {
            mediaEvents?.adUserInteraction(interactionType)
        } catch (e: Exception) {
            CriteoLogger.error("OMID ad user interaction error: ${e.localizedMessage}", Category.OMID)
        }
    }

    companion object {

        /**
         * Activates the OMID SDK. Should be called early in the application lifecycle.
         *
         * @param context Application context
         * @return true if activation successful, false otherwise
         */
        @JvmStatic
        fun activateOMSDK(context: Context): Boolean {
            if (Omid.isActive()) {
                CriteoLogger.info("OMID SDK already active", Category.OMID)
                return true
            }

            Omid.activate(context.applicationContext)

            val isActive = Omid.isActive()
            if (isActive) {
                CriteoLogger.info("OMID SDK activated successfully", Category.OMID)
            } else {
                CriteoLogger.error("Failed to activate OMID SDK", Category.OMID)
            }

            return isActive
        }

        /**
         * Checks if OMID SDK is currently active.
         *
         * @return true if OMID SDK is active
         */
        @JvmStatic
        fun isOMSDKActive(): Boolean = Omid.isActive()

        /**
         * For the simplicity of the demo project the OMID SDK javascript file is included in the
         * application bundle.
         *
         * In production, the JavaScript file should be hosted on a remote server.
         *
         * @param context - used to access the JS resource
         * @return - the Omid JS resource as a string
         */
        @JvmStatic
        fun getOmidJs(context: Context): String {
            val res = context.resources
            try {
                res.openRawResource(R.raw.omsdk_v1).use { inputStream ->
                    val b = ByteArray(inputStream.available())
                    val bytesRead = inputStream.read(b)
                    return String(b, 0, bytesRead, charset("UTF-8"))
                }
            } catch (e: IOException) {
                throw UnsupportedOperationException("Yikes, omid resource not found", e)
            }
        }
    }
}
