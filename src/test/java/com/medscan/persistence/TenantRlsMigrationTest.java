package com.medscan.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class TenantRlsMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void rlsHidesOtherTenantsAndRejectsCrossTenantWrites() throws SQLException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        try (Connection ownerConnection = ownerConnection()) {
            insertTenant(ownerConnection, tenantA, "tenant-a", "Organisation A");
            insertTenant(ownerConnection, tenantB, "tenant-b", "Organisation B");
            insertProbe(ownerConnection, UUID.randomUUID(), tenantA, "visible-to-a");
            insertProbe(ownerConnection, UUID.randomUUID(), tenantB, "visible-to-b");
        }

        try (Connection applicationConnection = ownerConnection()) {
            applicationConnection.createStatement().execute("SET ROLE medscan_app");

            assertEquals(0, probeLabels(applicationConnection).size());

            setTenantContext(applicationConnection, tenantA);

            assertEquals(List.of("visible-to-a"), probeLabels(applicationConnection));
            assertEquals(0, probeCountForTenant(applicationConnection, tenantB));
            assertThrows(SQLException.class,
                    () -> insertProbe(applicationConnection, UUID.randomUUID(), tenantB, "forbidden-write"));
        }
    }

    private Connection ownerConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void insertTenant(Connection connection, UUID id, String code, String displayName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO medscan.tenant (id, code, display_name) VALUES (?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, displayName);
            statement.executeUpdate();
        }
    }

    private void insertProbe(Connection connection, UUID id, UUID tenantId, String label) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO medscan.tenant_security_probe (id, tenant_id, label) VALUES (?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setObject(2, tenantId);
            statement.setString(3, label);
            statement.executeUpdate();
        }
    }

    private void setTenantContext(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('medscan.tenant_id', ?, false)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }

    private List<String> probeLabels(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT label FROM medscan.tenant_security_probe ORDER BY label")) {
            List<String> labels = new ArrayList<>();
            while (resultSet.next()) {
                labels.add(resultSet.getString("label"));
            }
            return labels;
        }
    }

    private int probeCountForTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM medscan.tenant_security_probe WHERE tenant_id = ?")) {
            statement.setObject(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
