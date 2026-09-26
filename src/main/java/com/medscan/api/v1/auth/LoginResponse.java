package com.medscan.api.v1.auth;

import java.io.Serializable;

/**
 * Authentication response payload containing JWT tokens and actor metadata.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user) implements Serializable {
}
