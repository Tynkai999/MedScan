package com.medscan.security.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TenantContextTest {

    @Test
    void rejectsAccessBeforeAServerVerifiedIdentityIsActivated() {
        TenantContext context = new TenantContext();

        assertThrows(TenantContextUnavailableException.class, context::requireTenantId);
        assertThrows(TenantContextUnavailableException.class, context::requireUserId);
    }

    @Test
    void exposesTheVerifiedIdentityAndPreventsTenantSwitching() {
        TenantContext context = new TenantContext();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        context.activate(new VerifiedTenantIdentity(tenantId, userId));

        assertEquals(tenantId, context.requireTenantId());
        assertEquals(userId, context.requireUserId());
        assertThrows(IllegalStateException.class,
                () -> context.activate(new VerifiedTenantIdentity(UUID.randomUUID(), userId)));
    }
}
