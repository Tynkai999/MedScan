package com.medscan.referral;

import java.time.Instant;
import java.util.UUID;

import com.medscan.certificate.HospitalSeal;

/**
 * Entité d'orientation et de redirection médicale inter-services et inter-établissements (MOD-16).
 * Permet à un médecin de transférer la prise en charge d'un patient vers un service spécialisé
 * ou un autre établissement de santé partenaire directement sur la plateforme MedScan.
 */
public record MedicalReferral(
        UUID id,
        String referralCode,
        UUID patientId,
        String patientName,
        String patientPhone,
        UUID sourceTenantId,
        String sourceTenantName,
        String sourceDepartment,
        UUID referringDoctorId,
        String referringDoctorName,
        UUID targetTenantId,
        String targetTenantName,
        String targetDepartment,
        UUID targetDoctorId,
        String targetDoctorName,
        String reason,
        String priority,
        String clinicalSummary,
        String status,
        String rejectionReason,
        HospitalSeal hospitalSeal,
        Instant createdAt,
        Instant updatedAt
) {

    public MedicalReferral withStatus(String newStatus, String rejReason, Instant now) {
        return new MedicalReferral(
                id,
                referralCode,
                patientId,
                patientName,
                patientPhone,
                sourceTenantId,
                sourceTenantName,
                sourceDepartment,
                referringDoctorId,
                referringDoctorName,
                targetTenantId,
                targetTenantName,
                targetDepartment,
                targetDoctorId,
                targetDoctorName,
                reason,
                priority,
                clinicalSummary,
                newStatus,
                rejReason != null ? rejReason : rejectionReason,
                hospitalSeal,
                createdAt,
                now != null ? now : Instant.now()
        );
    }
}
