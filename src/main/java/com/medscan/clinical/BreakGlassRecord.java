package com.medscan.clinical;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracé d'accès dérogatoire d'urgence (Break-Glass - MOD-04).
 * Enregistre le motif impérieux de levée de restriction pour audit réglementaire.
 */
public record BreakGlassRecord(
        UUID id,
        UUID patientId,
        UUID doctorId,
        String doctorName,
        UUID doctorTenantId,
        String reason,
        Instant timestamp,
        String emergencyLevel
) {
}
