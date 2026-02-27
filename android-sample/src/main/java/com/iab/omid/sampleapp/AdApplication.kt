package com.iab.omid.sampleapp

import android.app.Application
import com.iab.omid.sampleapp.manager.omid.OMIDSessionInteractorFactory

/**
 * AdApplication - application subclass. Init Omid SDK, AdLoader, and debug libraries
 *
 */
class AdApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize OMID SDK
        OMIDSessionInteractorFactory.activateOMSDK(this)
    }
}
