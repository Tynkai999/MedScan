package com.medscan.clinical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ordonnance médicale numérique sécurisée (MOD-08).
 * Suivie par code unique (ex: RX-2026-0042) pour la dispensation en pharmacie d'officine.
 */
public record Prescription(
        UUID id,
        String prescriptionCode,
        UUID patientId,
        String patientName,
        UUID doctorId,
        String doctorName,
        UUID tenantId,
        Instant issuedAt,
        String status, // ISSUED, DISPENSED, CANCELLED
        List<PrescriptionItem> items,
        String dispensedBy,
        Instant dispensedAt
) {
    public Prescription withDispensation(String pharmacistName, Instant when) {
        return new Prescription(
                id,
                prescriptionCode,
                patientId,
                patientName,
                doctorId,
                doctorName,
                tenantId,
                issuedAt,
                "DISPENSED",
                items,
                pharmacistName,
                when
        );
    }
}
