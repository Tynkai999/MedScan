package com.medscan.security.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.medscan.security.Role;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Registry of deterministic seed users representing all 10 system actors,
 * with full dynamic provisioning capabilities for Tenant Administrators and Super Admin.
 */
@ApplicationScoped
public class SeedUserRegistry {

    public static final UUID TENANT_HOSPITAL = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    public static final UUID TENANT_LAB = UUID.fromString("95705328-0183-4248-8da7-8691ecf284e6");
    public static final UUID TENANT_PHARMACY = UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89");
    public static final UUID TENANT_DELIVERY = UUID.fromString("ddfd4bda-a3a7-401a-a701-3ef502072705");
    public static final UUID TENANT_PLATFORM = UUID.fromString("b47c7913-35d0-43e3-8ec1-5614dec9ffcd");
    public static final UUID TENANT_HOSPITAL2 = UUID.fromString("f56de30f-c802-42c6-8587-707a8d1a9814");

    public static final String DEFAULT_PASSWORD = "Password123!";

    private final Map<String, ActorAccount> usersByUsername = new ConcurrentHashMap<>();
    private final Map<UUID, ActorAccount> usersById = new ConcurrentHashMap<>();

    public SeedUserRegistry() {
        register(new ActorAccount(
                UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4"),
                "patient@medscan.org",
                "patient@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Mme Fatou Ouedraogo (Patiente)",
                Set.of(Role.PATIENT.roleName()),
                Set.of("patient:read_own", "consent:manage", "appointment:book", "prescription:view_own")));

        register(new ActorAccount(
                UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0"),
                "doctor@medscan.org",
                "doctor@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Dr. Seydou Traore (Médecin Référent)",
                Set.of(Role.DOCTOR.roleName()),
                Set.of("patient:search", "consultation:write", "prescription:create", "exam:order", "break_glass:request")));

        register(new ActorAccount(
                UUID.fromString("acd191a6-4846-45d7-bd38-f0a042b59348"),
                "radiologist@medscan.org",
                "radiologist@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Dr. Aïssatou Sawadogo (Radiologue)",
                Set.of(Role.RADIOLOGIST.roleName()),
                Set.of("image:upload", "image:view", "ai:request_analysis", "report:validate")));

        register(new ActorAccount(
                UUID.fromString("0286d2e5-cb39-4aac-a944-fb0c13158caf"),
                "nurse@medscan.org",
                "nurse@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Inf. Mariam Diallo (Infirmière)",
                Set.of(Role.NURSE.roleName()),
                Set.of("vitals:record", "patient:view_summary", "care:administer")));

        register(new ActorAccount(
                UUID.fromString("2584bba9-1bef-40a6-bdd0-7dd333d1c8ef"),
                "labtech@medscan.org",
                "labtech@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_LAB,
                "LAB_CENTRAL",
                "Tech. Moussa Kabore (Technicien Labo)",
                Set.of(Role.LAB_TECHNICIAN.roleName()),
                Set.of("lab:upload_results", "exam:view_pending")));

        register(new ActorAccount(
                UUID.fromString("62cac0f6-d219-43ca-ab33-2571df0c7630"),
                "pharmacist@medscan.org",
                "pharmacist@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_PHARMACY,
                "PHARMACIE_AVENIR",
                "Dr. Pharm. Pascal Bado (Pharmacien Titulaire)",
                Set.of(Role.PHARMACIST.roleName()),
                Set.of("prescription:verify", "inventory:manage", "order:prepare")));

        register(new ActorAccount(
                UUID.fromString("e574676b-7f23-49a8-9528-127e5257311f"),
                "delivery@medscan.org",
                "delivery@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_DELIVERY,
                "LOGISTIQUE_SANTE",
                "Ibrahim Zongo (Agent de Livraison)",
                Set.of(Role.DELIVERY_AGENT.roleName()),
                Set.of("delivery:accept", "delivery:navigate", "delivery:complete_otp")));

        register(new ActorAccount(
                UUID.fromString("28856dce-4f9b-4850-b26a-4126b29faf32"),
                "tenantadmin@medscan.org",
                "tenantadmin@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_HOSPITAL,
                "CH_OUAGADOUGOU",
                "Admin Hôpital (Admin Tenant)",
                Set.of(Role.TENANT_ADMIN.roleName()),
                Set.of("tenant:manage_users", "tenant:manage_departments", "tenant:view_audit")));

        register(new ActorAccount(
                UUID.fromString("71989700-e940-4d4f-a42a-e69495f19552"),
                "superadmin@medscan.org",
                "superadmin@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_PLATFORM,
                "MEDSCAN_PLATFORM",
                "Super-Administrateur MedScan",
                Set.of(Role.SUPER_ADMIN.roleName()),
                Set.of("platform:manage_tenants", "platform:view_all_audit", "platform:configure")));

        register(new ActorAccount(
                UUID.fromString("f70a1335-b31f-4193-88ef-ce3460f3d029"),
                "auditor@medscan.org",
                "auditor@medscan.org",
                DEFAULT_PASSWORD,
                TENANT_PLATFORM,
                "MEDSCAN_PLATFORM",
                "Auditeur Sécurité & Conformité DPO",
                Set.of(Role.AUDITOR.roleName()),
                Set.of("audit:view", "audit:export", "compliance:review")));
    }

    public synchronized ActorAccount register(ActorAccount user) {
        if (user == null) {
            throw new IllegalArgumentException("L'utilisateur ne peut pas être nul.");
        }
        usersByUsername.put(user.username().toLowerCase(), user);
        usersById.put(user.userId(), user);
        return user;
    }

    /**
     * Crée et enregistre dynamiquement un membre du personnel soignant ou administratif.
     * Permet aux administrateurs d'établissement d'ajouter des médecins, infirmiers, pharmaciens, etc.
     */
    public ActorAccount createStaffUser(
            String username,
            String email,
            String password,
            String displayName,
            String roleName,
            UUID tenantId,
            String tenantCode
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        }
        String cleanUsername = username.trim().toLowerCase();
        if (usersByUsername.containsKey(cleanUsername)) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec cet identifiant: " + cleanUsername);
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Le mot de passe doit comporter au moins 6 caractères.");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Le tenantId est obligatoire.");
        }

        Role role = Role.fromString(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Rôle soignant ou administrateur invalide: " + roleName));

        Set<String> permissions = getDefaultPermissionsForRole(role);

        ActorAccount account = new ActorAccount(
                UUID.randomUUID(),
                cleanUsername,
                (email != null && !email.isBlank()) ? email.trim() : cleanUsername,
                password,
                tenantId,
                (tenantCode != null && !tenantCode.isBlank()) ? tenantCode : "TENANT_" + tenantId.toString().substring(0, 8),
                (displayName != null && !displayName.isBlank()) ? displayName.trim() : username,
                Set.of(role.roleName()),
                permissions
        );

        return register(account);
    }

    public static Set<String> getDefaultPermissionsForRole(Role role) {
        return switch (role) {
            case DOCTOR -> Set.of("patient:search", "patient:read", "consultation:write", "prescription:create", "exam:order", "break_glass:request");
            case NURSE -> Set.of("patient:search", "patient:read", "vitals:record", "vitals:write", "patient:view_summary", "care:administer");
            case PHARMACIST -> Set.of("prescription:verify", "prescription:dispense", "inventory:manage", "order:prepare");
            case RADIOLOGIST -> Set.of("image:upload", "image:view", "ai:request_analysis", "report:validate");
            case LAB_TECHNICIAN -> Set.of("lab:upload_results", "exam:view_pending");
            case DELIVERY_AGENT -> Set.of("delivery:accept", "delivery:navigate", "delivery:complete_otp");
            case TENANT_ADMIN -> Set.of("tenant:manage_users", "tenant:manage_departments", "tenant:view_audit");
            case SUPER_ADMIN -> Set.of("platform:manage_tenants", "platform:manage_users", "platform:view_all_audit", "platform:configure");
            case AUDITOR -> Set.of("audit:view", "audit:export", "compliance:review");
            case PATIENT -> Set.of("patient:read_own", "consent:manage", "appointment:book", "prescription:view_own");
        };
    }

    public boolean userExists(String username) {
        if (username == null) return false;
        return usersByUsername.containsKey(username.trim().toLowerCase());
    }

    public List<ActorAccount> findByTenantId(UUID tenantId) {
        if (tenantId == null) return Collections.emptyList();
        return usersById.values().stream()
                .filter(u -> tenantId.equals(u.tenantId()))
                .sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()))
                .collect(Collectors.toList());
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
        List<ActorAccount> list = new ArrayList<>(usersByUsername.values());
        list.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return Collections.unmodifiableList(list);
    }
}
