package com.virtualrift.apiscanner.engine;

import java.net.http.HttpResponse;
import java.util.Map;

public interface ApiClient {

    HttpResponse<String> sendRequest(String url, String method, Map<String, String> headers);

    HttpResponse<String> sendRequest(String url, String method);

    HttpResponse<String> sendRequestWithBody(String url, String method, String body);

    HttpResponse<String> sendRequestWithBody(String url, String method, String body, Map<String, String> headers);

    HttpResponse<String> sendRequestWithAuth(String url, String method, String token);
}
