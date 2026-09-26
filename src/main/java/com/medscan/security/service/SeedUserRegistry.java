package com.medscan.security.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.medscan.security.Role;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Registry of deterministic seed users representing all 10 system actors.
 * Enables immediate local development in Eclipse and automated testing,
 * synchronized with Keycloak pre-configured users.
 */
@ApplicationScoped
public class SeedUserRegistry {

    public static final UUID TENANT_HOSPITAL = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_LAB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID TENANT_PHARMACY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID TENANT_DELIVERY = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID TENANT_PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static final String DEFAULT_PASSWORD = "Password123!";

    private final Map<String, ActorAccount> usersByUsername = new HashMap<>();
    private final Map<UUID, ActorAccount> usersById = new HashMap<>();

    public SeedUserRegistry() {
        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "patient@medscan.org",
                "patient@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Mme Fatou Ouedraogo (Patiente)",
                Set.of(Role.PATIENT.roleName()),
                Set.of("patient:read_own", "consent:manage", "appointment:book", "prescription:view_own")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                "doctor@medscan.org",
                "doctor@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Dr. Seydou Traore (Médecin Référent)",
                Set.of(Role.DOCTOR.roleName()),
                Set.of("patient:search", "consultation:write", "prescription:create", "exam:order", "break_glass:request")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000003"),
                "radiologist@medscan.org",
                "radiologist@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Dr. Aïssatou Sawadogo (Radiologue)",
                Set.of(Role.RADIOLOGIST.roleName()),
                Set.of("image:upload", "image:view", "ai:request_analysis", "report:validate")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000004"),
                "nurse@medscan.org",
                "nurse@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Inf. Mariam Diallo (Infirmière)",
                Set.of(Role.NURSE.roleName()),
                Set.of("vitals:record", "patient:view_summary", "care:administer")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000005"),
                "labtech@medscan.org",
                "labtech@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_LAB,
                "LAB_CENTRAL",
                "Tech. Moussa Kabore (Technicien Labo)",
                Set.of(Role.LAB_TECHNICIAN.roleName()),
                Set.of("lab:upload_results", "exam:view_pending")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000006"),
                "pharmacist@medscan.org",
                "pharmacist@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_PHARMACY,
                "PHARMACIE_AVENIR",
                "Dr. Pharm. Pascal Bado (Pharmacien Titulaire)",
                Set.of(Role.PHARMACIST.roleName()),
                Set.of("prescription:verify", "inventory:manage", "order:prepare")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000007"),
                "delivery@medscan.org",
                "delivery@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_DELIVERY,
                "LOGISTIQUE_SANTE",
                "Ibrahim Zongo (Agent de Livraison)",
                Set.of(Role.DELIVERY_AGENT.roleName()),
                Set.of("delivery:accept", "delivery:navigate", "delivery:complete_otp")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000008"),
                "tenantadmin@medscan.org",
                "tenantadmin@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Admin Hôpital (Admin Tenant)",
                Set.of(Role.TENANT_ADMIN.roleName()),
                Set.of("tenant:manage_users", "tenant:manage_departments", "tenant:view_audit")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000009"),
                "superadmin@medscan.org",
                "superadmin@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_PLATFORM,
                "MEDSCAN_PLATFORM",
                "Super-Administrateur MedScan",
                Set.of(Role.SUPER_ADMIN.roleName()),
                Set.of("platform:manage_tenants", "platform:view_all_audit", "platform:configure")));

        register(new ActorAccount(
                UUID.fromString("10000000-0000-0000-0000-000000000010"),
                "auditor@medscan.org",
                "auditor@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_PLATFORM,
                "MEDSCAN_PLATFORM",
                "Auditeur Sécurité & Conformité DPO",
                Set.of(Role.AUDITOR.roleName()),
                Set.of("audit:view", "audit:export", "compliance:review")));
    }

    private void register(ActorAccount user) {
        usersByUsername.put(user.username().toLowerCase(), user);
        usersById.put(user.userId(), user);
    }

    public Optional<ActorAccount> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByUsername.get(username.trim().toLowerCase()));
    }

    public Optional<ActorAccount> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersById.get(id));
    }

    public Collection<ActorAccount> allActors() {
        return Collections.unmodifiableCollection(usersByUsername.values());
    }
}
