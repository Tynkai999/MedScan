package com.medscan.api.v1.auth;

import java.io.Serializable;

/**
 * Request payload for actor authentication.
 */
public record LoginRequest(String username, String password) implements Serializable {
}
