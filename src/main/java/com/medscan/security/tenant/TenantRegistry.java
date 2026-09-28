package com.medscan.security.tenant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre dynamique des établissements et structures de santé partenaires (MOD-01).
 * Élimine tout identifiant en dur dans le code en permettant l'ajout dynamique
 * de structures par les administrateurs et le super-administrateur.
 */
public class TenantRegistry {

    public static final UUID PLATFORM_TENANT_ID = UUID.fromString("b47c7913-35d0-43e3-8ec1-5614dec9ffcd");

    private final Map<UUID, Tenant> tenantsById = new ConcurrentHashMap<>();
    private final Map<String, Tenant> tenantsByCode = new ConcurrentHashMap<>();

    public TenantRegistry() {
        initDefaultTenants();
    }

    /**
     * Initialise les structures de santé de référence (Burkina Faso / Afrique de l'Ouest)
     */
    private void initDefaultTenants() {
        register(new Tenant(
                UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a"),
                "CH_OUAGADOUGOU",
                "Centre Hospitalier Universitaire de Ouagadougou",
                "HOSPITAL",
                "Burkina Faso",
                "Ouagadougou",
                "+226 25 30 66 44",
                "contact@ch-ouaga.bf",
                "Avenue de l'Hôpital, Ouagadougou",
                "ACTIVE",
                Instant.now()
        ));

        register(new Tenant(
                UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89"),
                "PHARMA_CENTRALE",
                "Grande Pharmacie Centrale & Officine Avenir",
                "PHARMACY",
                "Burkina Faso",
                "Ouagadougou",
                "+226 25 31 12 34",
                "officine@pharma-centrale.bf",
                "Boulevard Charles de Gaulle",
                "ACTIVE",
                Instant.now()
        ));

        register(new Tenant(
                UUID.fromString("95705328-0183-4248-8da7-8691ecf284e6"),
                "LAB_BIO_SANTE",
                "Laboratoire National de Biologie Médicale & Diagnostics",
                "LABORATORY",
                "Burkina Faso",
                "Ouagadougou",
                "+226 25 33 45 67",
                "analyses@lab-biosante.bf",
                "Rue de la Santé",
                "ACTIVE",
                Instant.now()
        ));

        register(new Tenant(
                UUID.fromString("ddfd4bda-a3a7-401a-a701-3ef502072705"),
                "EXPRESS_MEDIC",
                "Service Logistique & Livraison Médicale Express",
                "LOGISTICS",
                "Burkina Faso",
                "Ouagadougou",
                "+226 70 88 99 00",
                "dispatch@express-medic.bf",
                "Zone d'Activité Logistique",
                "ACTIVE",
                Instant.now()
        ));

        register(new Tenant(
                PLATFORM_TENANT_ID,
                "MEDSCAN_SYS",
                "Système Central & Plateforme Souveraine MedScan",
                "PLATFORM",
                "Burkina Faso",
                "Ouagadougou",
                "+226 25 00 00 00",
                "admin@medscan.org",
                "Siège MedScan Digital Health",
                "ACTIVE",
                Instant.now()
        ));

        register(new Tenant(
                UUID.fromString("f56de30f-c802-42c6-8587-707a8d1a9814"),
                "CLINIQUE_DES_ALBIES",
                "Clinique Médico-Chirurgicale des Albies",
                "CLINIC",
                "Burkina Faso",
                "Bobo-Dioulasso",
                "+226 20 97 11 22",
                "accueil@clinique-albies.bf",
                "Secteur 5, Bobo-Dioulasso",
                "ACTIVE",
                Instant.now()
        ));
    }

    public synchronized Tenant register(Tenant tenant) {
        if (tenant == null) {
            throw new IllegalArgumentException("Le tenant ne peut être nul.");
        }
        tenantsById.put(tenant.id(), tenant);
        tenantsByCode.put(tenant.code().toUpperCase(), tenant);
        return tenant;
    }

    public Optional<Tenant> findById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(tenantsById.get(id));
    }

    public Optional<Tenant> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(tenantsByCode.get(code.trim().toUpperCase()));
    }

    public List<Tenant> listAllTenants() {
        List<Tenant> list = new ArrayList<>(tenantsById.values());
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return Collections.unmodifiableList(list);
    }

    public boolean exists(UUID id) {
        return id != null && tenantsById.containsKey(id);
    }

    /**
     * Résout dynamiquement le code ou libellé court du tenant à partir du registre
     * SANS AUCUN IDENTIFIANT EN DUR DANS LE CODE.
     */
    public String resolveTenantName(UUID tenantId) {
        if (tenantId == null) {
            return "MEDSCAN_SYS";
        }
        Tenant t = tenantsById.get(tenantId);
        if (t != null) {
            return t.code();
        }
        return "ORGANISATION_SANTE";
    }

    /**
     * Résout le libellé complet affichable pour les tableaux de bord et dossiers
     */
    public String resolveTenantDisplayName(UUID tenantId) {
        if (tenantId == null) {
            return "Système Central MedScan";
        }
        Tenant t = tenantsById.get(tenantId);
        if (t != null) {
            return t.name();
        }
        return "Établissement Partenaire (" + tenantId + ")";
    }

    public Tenant createTenant(String code, String name, String type, String country, String city, String phone, String email, String address) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de l'établissement est obligatoire.");
        }
        String safeCode = (code != null && !code.isBlank()) ? code.trim().toUpperCase() : generateCodeFromName(name);
        safeCode = safeCode.replaceAll("[\\s-]+", "_");
        if (tenantsByCode.containsKey(safeCode)) {
            throw new IllegalArgumentException("Une structure de santé existe déjà avec le code: " + safeCode);
        }

        Tenant tenant = new Tenant(
                UUID.randomUUID(),
                safeCode,
                name.trim(),
                type,
                country,
                city,
                phone,
                email,
                address,
                "ACTIVE",
                Instant.now()
        );
        return register(tenant);
    }

    private String generateCodeFromName(String name) {
        String base = name.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        return base + "_" + (int)(Math.random() * 900 + 100);
    }
}
