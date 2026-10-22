package com.medscan.appointment;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité Rendez-vous médical & Session de Téléconsultation (MOD-12).
 * Conforme : Zéro Trust, Agenda partagé multi-praticiens (Médecin, Infirmier, Technicien, Radiologue).
 */
public record Appointment(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String patientName,
        UUID practitionerId,
        String practitionerName,
        String practitionerRole,
        String appointmentType, // IN_PERSON, TELECONSULTATION, LAB_ANALYSIS, IMAGING, NURSING
        Instant scheduledAt,
        int durationMinutes,
        String status, // REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED
        String reason,
        String clinicalNotes,
        String teleconsultationRoomId,
        String teleconsultationJoinUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public Appointment withStatus(String newStatus, String notes) {
        return new Appointment(
                id,
                tenantId,
                patientId,
                patientName,
                practitionerId,
                practitionerName,
                practitionerRole,
                appointmentType,
                scheduledAt,
                durationMinutes,
                newStatus,
                reason,
                notes != null ? notes : clinicalNotes,
                teleconsultationRoomId,
                teleconsultationJoinUrl,
                createdAt,
                Instant.now()
        );
    }
}
