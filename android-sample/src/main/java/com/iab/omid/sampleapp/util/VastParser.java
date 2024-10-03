package com.iab.omid.sampleapp.util;

import android.os.Handler;
import android.os.Looper;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class VastParser {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public interface VASTFetchCallback {
        void onSuccess(Document doc);
        void onFailure(Exception e);
    }

    public void fetchAndParseVast(String vastUrl, VASTFetchCallback callback) {
        executorService.submit(() -> {
            Document doc;
            try {
                doc = parseVastXml(vastUrl);
            } catch (Exception e) {
                mainThreadHandler.post(() -> callback.onFailure(e));
                return;
            }
            Document finalDoc = doc;
            mainThreadHandler.post(() -> callback.onSuccess(finalDoc));
        });
    }

    public static Document parseVastXml(String xmlData) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlData);
        doc.getDocumentElement().normalize();

        return doc;
    }
}
