package com.rocketpj.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfoResponse user
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserInfoResponse user) {
        this(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
