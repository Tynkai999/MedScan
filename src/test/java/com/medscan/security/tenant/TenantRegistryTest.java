package com.medscan.security.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantRegistryTest {

    private TenantRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
    }

    @Test
    @DisplayName("Les structures de référence sont initialisées et accessibles")
    void testInitialTenants() {
        List<Tenant> tenants = registry.listAllTenants();
        assertTrue(tenants.size() >= 6, "Le registre doit contenir au moins 6 structures de base");

        Optional<Tenant> ouagaOpt = registry.findByCode("CH_OUAGADOUGOU");
        assertTrue(ouagaOpt.isPresent());
        assertEquals("Centre Hospitalier Universitaire de Ouagadougou", ouagaOpt.get().name());
        assertEquals("HOSPITAL", ouagaOpt.get().type());

        Optional<Tenant> pharmaOpt = registry.findByCode("PHARMA_CENTRALE");
        assertTrue(pharmaOpt.isPresent());
        assertEquals("PHARMACY", pharmaOpt.get().type());
    }

    @Test
    @DisplayName("Résolution dynamique du nom d'établissement sans aucun code en dur")
    void testDynamicResolveTenantName() {
        UUID ouagaId = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
        assertEquals("CH_OUAGADOUGOU", registry.resolveTenantName(ouagaId));

        UUID pharmaId = UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89");
        assertEquals("PHARMA_CENTRALE", registry.resolveTenantName(pharmaId));

        UUID platformId = TenantRegistry.PLATFORM_TENANT_ID;
        assertEquals("MEDSCAN_SYS", registry.resolveTenantName(platformId));

        // UUID inconnu renvoie le fallback générique
        assertEquals("ORGANISATION_SANTE", registry.resolveTenantName(UUID.randomUUID()));
        assertEquals("MEDSCAN_SYS", registry.resolveTenantName(null));
    }

    @Test
    @DisplayName("Création dynamique d'une nouvelle structure par l'administrateur")
    void testCreateNewTenantDynamically() {
        Tenant created = registry.createTenant(
                "POLYCLINIQUE_OUAGA",
                "Polyclinique Internationale de Ouagadougou",
                "CLINIC",
                "Burkina Faso",
                "Ouagadougou",
                "+226 25 40 50 60",
                "info@polyclinique-ouaga.bf",
                "Quartier Ouaga 2000"
        );

        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("POLYCLINIQUE_OUAGA", created.code());
        assertEquals("CLINIC", created.type());
        assertEquals("ACTIVE", created.status());

        // Vérification de la présence dans le registre
        assertTrue(registry.exists(created.id()));
        assertEquals("POLYCLINIQUE_OUAGA", registry.resolveTenantName(created.id()));
        assertEquals("Polyclinique Internationale de Ouagadougou", registry.resolveTenantDisplayName(created.id()));

        // Recherche par code
        Optional<Tenant> retrieved = registry.findByCode("POLYCLINIQUE_OUAGA");
        assertTrue(retrieved.isPresent());
        assertEquals(created.id(), retrieved.get().id());

        // Tentative de doublon de code rejetée
        assertThrows(IllegalArgumentException.class, () -> {
            registry.createTenant("POLYCLINIQUE_OUAGA", "Autre Nom", "CLINIC", "BF", "Ouaga", "", "", "");
        });
    }
}
