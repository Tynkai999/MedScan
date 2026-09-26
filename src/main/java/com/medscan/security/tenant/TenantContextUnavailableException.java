package com.medscan.security.tenant;

public class TenantContextUnavailableException extends IllegalStateException {

    public TenantContextUnavailableException() {
        super("No verified tenant context is active for this request.");
    }
}
