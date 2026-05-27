package com.virtualrift.webscanner.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface HttpClient {

    default Optional<String> sendRequest(String url, String payload, Map<String, String> headers, Map<String, String> cookies) {
        return sendRequest(url, payload);
    }

    default Optional<String> getPage(String url, Map<String, String> headers, Map<String, String> cookies) {
        return getPage(url);
    }

    Optional<String> sendRequest(String url, String payload);

    Optional<String> getPage(String url);

    Optional<String> sendRequestWithCookie(String url, String payload, String cookieName, String cookieValue);

    Optional<String> sendRequestWithCookie(String url, String payload);

    Optional<String> sendRequestWithHeader(String url, String payload, String headerName, String headerValue);

    Optional<String> sendRequestWithHeader(String url, String payload);

    default Optional<String> sendJson(String url, String jsonPayload, Map<String, String> headers, Map<String, String> cookies) {
        return sendJson(url, jsonPayload);
    }

    Optional<String> sendJson(String url, String jsonPayload);

    default List<String> fetchJavaScript(String url, Map<String, String> headers, Map<String, String> cookies) {
        return fetchJavaScript(url);
    }

    List<String> fetchJavaScript(String url);

    long measureResponseTime(String url);

    default HttpClient withContext(Map<String, String> headers, Map<String, String> cookies) {
        return this;
    }
}
