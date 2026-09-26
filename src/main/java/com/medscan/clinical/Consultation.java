package com.medscan.clinical;

import java.time.Instant;
import java.util.UUID;

/**
 * Consultation médicale et notes cliniques (MOD-03).
 */
public record Consultation(
        UUID id,
        UUID patientId,
        UUID doctorId,
        String doctorName,
        UUID tenantId,
        Instant date,
        String chiefComplaint,
        String examinationNotes,
        String diagnosis,
        String treatmentPlan
) {
}
