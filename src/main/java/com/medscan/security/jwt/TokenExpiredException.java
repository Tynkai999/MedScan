package com.medscan.security.jwt;

public class TokenExpiredException extends InvalidTokenException {

    public TokenExpiredException(String message) {
        super(message);
    }
}
