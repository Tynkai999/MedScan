package com.medscan.grievance;

import java.time.Instant;
import java.util.UUID;

/**
 * Réclamation, avis déontologique ou signalement de patient sur un médecin (MOD-18).
 * Directement acheminé et lisible par l'administrateur hospitalier (TENANT_ADMIN)
 * pour la gestion de la qualité des soins et des litiges médicaux.
 */
public record PatientGrievance(
        UUID id,
        String grievanceCode,
        UUID patientId,
        String patientName,
        String patientPhone,
        UUID doctorId,
        String doctorName,
        UUID tenantId,
        String hospitalName,
        UUID consultationId,
        String grievanceType,    // COMPLAINT, INCIDENT, FEEDBACK, COMMENDATION
        String category,         // BEHAVIOR_ETIQUETTE, DELAY_WAIT_TIME, CARE_QUALITY, PRESCRIPTION_ISSUE, ETHICAL_CONCERN, OTHER
        int rating,              // 1 à 5 étoiles
        String severity,         // LOW, MEDIUM, HIGH, CRITICAL
        String title,
        String description,
        String status,           // SUBMITTED, UNDER_REVIEW, RESOLVED, DISMISSED
        String adminNotes,       // Remarques de l'administrateur de l'hôpital
        String adminActionTaken, // Mesures prises (ex: entretien de recadrage, médiation, rappel protocolaire)
        UUID reviewedByAdminId,
        String reviewedByAdminName,
        Instant reviewedAt,
        Instant createdAt
) {

    public PatientGrievance withReview(
            String newStatus,
            String notes,
            String actionTaken,
            UUID adminId,
            String adminName,
            Instant now
    ) {
        return new PatientGrievance(
                id,
                grievanceCode,
                patientId,
                patientName,
                patientPhone,
                doctorId,
                doctorName,
                tenantId,
                hospitalName,
                consultationId,
                grievanceType,
                category,
                rating,
                severity,
                title,
                description,
                newStatus != null ? newStatus : status,
                notes != null ? notes : adminNotes,
                actionTaken != null ? actionTaken : adminActionTaken,
                adminId != null ? adminId : reviewedByAdminId,
                adminName != null ? adminName : reviewedByAdminName,
                now != null ? now : Instant.now(),
                createdAt
        );
    }
}
