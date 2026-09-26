package com.medscan.clinical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entité Patient du Dossier Médical Partagé (MOD-03).
 * Respecte l'isolation multi-tenant stricte et le Master Patient Index (MPI).
 */
public record Patient(
        UUID id,
        String nationalId,
        String firstName,
        String lastName,
        String birthDate,
        String gender,
        String bloodGroup,
        String phone,
        String emergencyContact,
        List<String> allergies,
        List<String> chronicConditions,
        UUID tenantId,
        UUID linkedUserId,
        Instant createdAt
) {
    public String fullName() {
        return firstName + " " + lastName;
    }
}
