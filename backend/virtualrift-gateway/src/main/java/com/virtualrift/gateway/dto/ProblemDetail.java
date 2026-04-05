package com.virtualrift.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

 @param type       
 @param title     
 @param status     
 @param detail     
 @param instance   
 @param tenantId   
 @param errors     

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String tenantId,
        Map<String, String> errors
) {
    public static ProblemDetail of(int status, String title, String detail, String instance) {
        return new ProblemDetail(
                "https://virtualrift.io/errors/" + toKebabCase(title),
                title,
                status,
                detail,
                instance,
                null,
                null
        );
    }

    public static ProblemDetail unauthorized(String detail, String instance) {
        return of(401, "Unauthorized", detail, instance);
    }

    public static ProblemDetail rateLimitExceeded(String detail, String instance) {
        return of(429, "Rate Limit Exceeded", detail, instance);
    }

    public static ProblemDetail badRequest(String detail, String instance) {
        return of(400, "Bad Request", detail, instance);
    }

    public static ProblemDetail internalServerError(String detail, String instance) {
        return of(500, "Internal Server Error", detail, instance);
    }

    private static String toKebabCase(String input) {
        return input.toLowerCase()
                .replace(" ", "-")
                .replace("_", "-");
    }
}
