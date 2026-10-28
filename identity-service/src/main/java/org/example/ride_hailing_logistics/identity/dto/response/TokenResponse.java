package org.example.ride_hailing_logistics.identity.dto.response;

import java.time.Instant;

public record TokenResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                            Instant refreshTokenExpiresAt, String tokenType, UserResponse user) {}
