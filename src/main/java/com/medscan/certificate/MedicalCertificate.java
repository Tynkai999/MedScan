package com.medscan.certificate;

import java.time.Instant;
import java.util.UUID;

/**
 * Certificat médical officiel et justificatif d'état de santé scellé par l'hôpital (MOD-17).
 * Fait foi auprès des employeurs, des universités/écoles et des caisses d'assurance maladie.
 */
public record MedicalCertificate(
        UUID id,
        String certificateCode,
        UUID patientId,
        String patientName,
        String patientNationalId,
        String patientBirthDate,
        UUID doctorId,
        String doctorName,
        String doctorLicenseNumber,
        UUID tenantId,
        String hospitalName,
        String certificateType,       // SICK_LEAVE, STUDENT_DISPENSE, ATTENDANCE_PROOF, PRESCRIPTION_CERTIFIED, FITNESS_CERTIFICATE
        String diagnosisSummary,     // Formulation respectant le secret médical
        Instant startDate,
        Instant endDate,
        int durationDays,
        String recipientType,        // EMPLOYER, UNIVERSITY_SCHOOL, INSURANCE, GENERAL
        String recipientName,        // ex: "Direction des Ressources Humaines - SONABEL" ou "Université Joseph Ki-Zerbo"
        String restrictions,         // ex: "Repos strict à domicile", "Dispense d'épreuves physiques et sportives"
        HospitalSeal hospitalSeal,   // Cachet officiel de l'établissement
        String status,               // ISSUED, REVOKED
        Instant createdAt
) {
}
