package com.virtualrift.webscanner.engine;

import java.util.List;
import java.util.Optional;

public interface HttpClient {

    Optional<String> sendRequest(String url, String payload);

    Optional<String> getPage(String url);

    Optional<String> sendRequestWithCookie(String url, String payload, String cookieName, String cookieValue);

    Optional<String> sendRequestWithCookie(String url, String payload);

    Optional<String> sendRequestWithHeader(String url, String payload, String headerName, String headerValue);

    Optional<String> sendRequestWithHeader(String url, String payload);

    Optional<String> sendJson(String url, String jsonPayload);

    List<String> fetchJavaScript(String url);

    long measureResponseTime(String url);
}
