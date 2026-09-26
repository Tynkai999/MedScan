package com.medscan.imaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Compte-rendu médical officiel signé par le médecin ou radiologue (MOD-05).
 * Valide, corrige ou rejette les suggestions du modèle d'IA pour conformité légale.
 */
public record RadiologistReport(
        UUID id,
        UUID studyId,
        UUID doctorId,
        String doctorName,
        String doctorRole, // RADIOLOGIST, DOCTOR
        Instant validatedAt,
        String conclusion,
        String aiAgreementStatus, // AGREED, MODIFIED, REJECTED
        String recommendedActions
) {
}
