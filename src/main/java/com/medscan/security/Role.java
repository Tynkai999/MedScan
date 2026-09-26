package com.medscan.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standard system roles for MedScan Enterprise actors.
 * Corresponds to Keycloak realm roles and RBAC security constraints.
 */
public enum Role {

    PATIENT("PATIENT", "Patient usager du système de soins"),
    DOCTOR("DOCTOR", "Médecin praticien (généraliste / spécialiste)"),
    RADIOLOGIST("RADIOLOGIST", "Médecin radiologue ou spécialiste imagerie"),
    NURSE("NURSE", "Infirmier / soignant"),
    LAB_TECHNICIAN("LAB_TECHNICIAN", "Technicien de laboratoire d'analyses"),
    PHARMACIST("PHARMACIST", "Pharmacien d'officine"),
    DELIVERY_AGENT("DELIVERY_AGENT", "Agent de livraison pharmaceutique"),
    TENANT_ADMIN("TENANT_ADMIN", "Administrateur d'établissement de santé"),
    SUPER_ADMIN("SUPER_ADMIN", "Super-administrateur de la plateforme MedScan"),
    AUDITOR("AUDITOR", "Auditeur de sécurité / DPO");

    private final String roleName;
    private final String description;

    Role(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    public String roleName() {
        return roleName;
    }

    public String description() {
        return description;
    }

    public static Optional<Role> fromString(String role) {
        if (role == null || role.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(r -> r.roleName.equalsIgnoreCase(role.trim()))
                .findFirst();
    }

    public static Set<String> allRoleNames() {
        return Collections.unmodifiableSet(
                Arrays.stream(values())
                        .map(Role::roleName)
                        .collect(Collectors.toSet()));
    }
}
