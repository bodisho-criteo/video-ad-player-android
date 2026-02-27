package com.iab.omid.sampleapp.manager.vast

import java.net.URL

/**
 * Represents a single `<MediaFile>` rendition inside a VAST document.
 *
 * A VAST response can contain multiple `<MediaFile>` elements providing different encodings,
 * resolutions, or MIME types of the same creative so the player can select the most appropriate
 * rendition (e.g. based on bandwidth, device capabilities, or supported codecs).
 *
 * This data model keeps only lightweight metadata required for selection plus an optional
 * nested `<ClosedCaptionFile>` URL if the caption file is declared inside the `<MediaFile>` block.
 * (Global caption files that appear outside a `<MediaFile>` are exposed separately in [VastAd].)
 *
 * Typical selection heuristics you might apply externally:
 *  - Prefer MIME type `video/mp4` for broad compatibility.
 *  - Prefer the rendition whose width/height best matches the viewport while minimizing upscale.
 *  - Fallback to the first available file if none match exact criteria.
 *
 * @property url Absolute URL of the media asset.
 * @property width The declared pixel width of the video rendition (may be null if not specified).
 * @property height The declared pixel height of the video rendition (may be null if not specified).
 * @property type MIME type / delivery format (e.g. `video/mp4`, `video/webm`). May be null.
 * @property captionUrl Optional caption / subtitle file URL if provided within this `<MediaFile>`.
 */
 data class VastMediaFile(
    val url: URL,
    val width: Int? = null,
    val height: Int? = null,
    val type: String? = null,
    val captionUrl: URL? = null
 )
