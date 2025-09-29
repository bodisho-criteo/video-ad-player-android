package com.iab.omid.sampleapp.util

import android.os.Handler
import android.os.Looper
import org.w3c.dom.Document
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.xml.parsers.DocumentBuilderFactory

class VastParser {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    fun fetchAndParseVast(
        vastUrl: String,
        onSuccess: (Document) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        executorService.submit {
            runCatching { parseVastXml(vastUrl) }
                .onSuccess { document -> mainThreadHandler.post { onSuccess(document) } }
                .onFailure { error -> mainThreadHandler.post { onFailure(error) } }
        }
    }

    private fun parseVastXml(xmlUrl: String): Document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(xmlUrl)
        .apply { documentElement.normalize() }
}
