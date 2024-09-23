package com.iab.omid.sampleapp.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class VastParser {

    private final OkHttpClient httpClient = new OkHttpClient();
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
                // Notify the callback about the failure
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

    public void sendTrackingRequest(String url) {
        executorService.submit(() -> {
            try {
                // Perform the HTTP request to the tracker URL using OkHttp
                Request request = new Request.Builder().url(url).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response code: " + response);
                    }
                    System.out.println("Start tracker called successfully: " + response.code());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
