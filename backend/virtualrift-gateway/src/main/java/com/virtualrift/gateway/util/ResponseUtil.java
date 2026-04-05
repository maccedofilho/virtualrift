package com.virtualrift.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.gateway.dto.ProblemDetail;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public final class ResponseUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ResponseUtil() {
    }

    public static Mono<Void> writeProblemDetail(
            ServerWebExchange exchange,
            HttpStatusCode statusCode,
            ProblemDetail problemDetail) {
        exchange.getResponse().setStatusCode(statusCode);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(problemDetail);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
            } catch (JsonProcessingException e) {
            byte[] bytes = "{\"error\":\"Internal server error\"}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }

    public static Mono<Void> noContent() {
        return Mono.empty();
    }
}
