package com.medscan.security.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TenantDatabaseContext {

    private final TenantContext tenantContext;

    @Inject
    public TenantDatabaseContext(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    /**
     * Binds the verified request tenant to the current database transaction.
     * Call this only after the JTA transaction has begun and before tenant-aware SQL executes.
     */
    public void applyToCurrentTransaction(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection is required");

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('medscan.tenant_id', ?, true)")) {
            statement.setString(1, tenantContext.requireTenantId().toString());
            statement.execute();
        }
    }
}
