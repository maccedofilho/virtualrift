package com.virtualrift.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {}
