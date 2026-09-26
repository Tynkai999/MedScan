package com.medscan.clinical;

import java.time.Instant;
import java.util.UUID;

/**
 * Constantes vitales du patient (Tension, Pouls, Température, Poids, Glycémie).
 */
public record VitalSigns(
        UUID id,
        UUID patientId,
        Instant recordedAt,
        Integer systolicBp,
        Integer diastolicBp,
        Integer heartRate,
        Double temperature,
        Double weightKg,
        Double bloodGlucose,
        String recordedBy,
        String recordedByRole
) {
}
