package com.iab.omid.sampleapp.manager.vast

import com.iab.omid.sampleapp.util.CriteoLogger
import com.iab.omid.sampleapp.util.CriteoLogger.Category
import java.io.InputStream
import java.io.StringReader
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

/**
 * Parses raw VAST XML into a [VastAd] model using DOM + XPath.
 */
class VastManager {

    private val documentBuilder by lazy { DocumentBuilderFactory.newInstance().newDocumentBuilder() }
    private val xPath by lazy { XPathFactory.newInstance().newXPath() }

    fun parseVast(xml: String): VastAd = try {
        buildAd(parseDocument(InputSource(StringReader(xml))))
    } catch (e: Exception) {
        CriteoLogger.error("Failed to parse VAST from String: ${e.localizedMessage}", Category.VAST)
        VastAd()
    }

    fun parseVast(stream: InputStream): VastAd = try {
        buildAd(parseDocument(InputSource(stream)))
    } catch (e: Exception) {
        CriteoLogger.error("Failed to parse VAST from stream: ${e.localizedMessage}", Category.VAST)
        VastAd()
    }

    private fun parseDocument(src: InputSource): Document = documentBuilder
        .parse(src)
        .apply { documentElement.normalize() }

    private fun buildAd(document: Document): VastAd {
        val verificationElem = querySelector(document, "//Verification")

        return VastAd(
            videoUrl = extractMediaFiles(document).firstOrNull()?.url,
            mediaFiles = extractMediaFiles(document),
            duration = queryText(document, "//Duration"),
            impressionUrls = queryUrls(document, "//Impression"),
            errorUrls = queryUrls(document, "//Error"),
            trackingEvents = extractTrackingEvents(document, "//Linear/TrackingEvents/Tracking"),
            clickTrackingUrls = queryUrls(document, "//ClickTracking"),
            clickThroughUrl = queryUrl(document, "//ClickThrough"),
            closedCaptionUrl = queryUrl(document, "//ClosedCaptionFile"),
            verificationScriptUrl = verificationElem?.let { queryUrl(it, "JavaScriptResource") },
            verificationParameters = verificationElem?.let { queryText(it, "VerificationParameters") },
            verificationTracking = verificationElem?.let { extractTrackingEvents(it, "TrackingEvents/Tracking") } ?: emptyMap(),
            vendorKey = verificationElem?.getAttribute("vendor")?.takeIf { it.isNotBlank() }
        )
    }

    private fun extractMediaFiles(document: Document): List<VastMediaFile> =
        queryElements(document, "//MediaFile").mapNotNull { elem ->
            queryUrl(elem)?.let { url ->
                VastMediaFile(
                    url = url,
                    width = elem.getAttribute("width").toIntOrNull(),
                    height = elem.getAttribute("height").toIntOrNull(),
                    type = elem.getAttribute("type").takeIf { it.isNotBlank() },
                    captionUrl = null
                )
            }
        }

    private fun extractTrackingEvents(context: Any, xpath: String): Map<String, URL> =
        queryElements(context, xpath).mapNotNull { elem ->
            val event = elem.getAttribute("event").takeIf { it.isNotBlank() }
            val url = queryUrl(elem)
            if (event != null && url != null) event to url else null
        }.toMap()

    // Core query functions
    private fun queryElements(context: Any, xpath: String): List<Element> = try {
        if (context is Document || context is Element) {
            val nodeList = xPath.evaluate(xpath, context, XPathConstants.NODESET) as NodeList
            List(nodeList.length) { nodeList.item(it) }.mapNotNull { it as? Element }
        } else emptyList()
    } catch (e: Exception) {
        CriteoLogger.warning("XPath query failed for '$xpath': ${e.localizedMessage}", Category.VAST)
        emptyList()
    }

    private fun querySelector(context: Any, xpath: String): Element? =
        queryElements(context, xpath).firstOrNull()

    private fun queryText(context: Any, xpath: String = ""): String? {
        val text = if (xpath.isEmpty() && context is Element) {
            context.textContent
        } else {
            querySelector(context, xpath)?.textContent
        }
        return text?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun queryUrl(context: Any, xpath: String = ""): URL? =
        queryText(context, xpath)?.let { urlString ->
            runCatching { URL(urlString) }
                .onFailure { CriteoLogger.warning("Malformed URL: $urlString", Category.VAST) }
                .getOrNull()
        }

    private fun queryUrls(context: Any, xpath: String): List<URL> =
        queryElements(context, xpath).mapNotNull { queryUrl(it) }
}
