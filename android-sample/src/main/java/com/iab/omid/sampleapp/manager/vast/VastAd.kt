package com.iab.omid.sampleapp.manager.vast

import java.net.URL

/**
 * Immutable data class representing a VAST (Video Ad Serving Template) ad.
 *
 * @property videoUrl The URL of the video asset.
 * @property mediaFiles All available MediaFile renditions parsed from the VAST.
 * @property duration The duration of the video in HH:MM:SS format.
 * @property impressionUrls List of beacon URLs to be pinged when the ad is viewed.
 * @property errorUrls List of beacon URLs to be pinged in case of an error.
 * @property trackingEvents Map of event names ("start", "firstQuartile", etc.) to beacon URLs.
 * @property clickTrackingUrls List of beacon URLs to be pinged when the ad is clicked.
 * @property clickThroughUrl The URL to navigate to when the ad is clicked.
 * @property closedCaptionUrl The URL of the closed caption file.
 * @property verificationScriptUrl The URL of the verification script for ad verification.
 * @property verificationParameters Parameters for the verification script.
 * @property verificationTracking Map of verification event names to tracking URLs.
 * @property vendorKey The vendor key for ad verification.
 */
data class VastAd(
    val videoUrl: URL? = null,
    val mediaFiles: List<VastMediaFile> = emptyList(),
    val duration: String? = null,
    val impressionUrls: List<URL> = emptyList(),
    val errorUrls: List<URL> = emptyList(),
    val trackingEvents: Map<String, URL> = emptyMap(),
    val clickTrackingUrls: List<URL> = emptyList(),
    val clickThroughUrl: URL? = null,
    val closedCaptionUrl: URL? = null,
    val verificationScriptUrl: URL? = null,
    val verificationParameters: String? = null,
    val verificationTracking: Map<String, URL> = emptyMap(),
    val vendorKey: String? = null
)
