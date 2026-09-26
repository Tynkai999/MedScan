package com.medscan.api.v1.auth;

import java.io.Serializable;

/**
 * Request payload for refreshing an access token.
 */
public record RefreshTokenRequest(String refreshToken) implements Serializable {
}
