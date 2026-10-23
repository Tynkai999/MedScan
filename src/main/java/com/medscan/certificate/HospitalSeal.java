package com.medscan.certificate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Cachet numérique et sceau d'authentification officiel de la structure de santé (MOD-17).
 * Scelle les ordonnances, certificats médicaux, arrêts de travail et dispenses universitaires.
 */
public record HospitalSeal(
        UUID tenantId,
        String hospitalName,
        String hospitalCode,
        String hospitalAddress,
        String hospitalPhone,
        String hospitalEmail,
        String accreditationNumber,
        Instant stampedAt,
        String stampedByDoctorName,
        String doctorLicenseNumber,
        String verificationHash,
        String verificationUrl
) {

    /**
     * Génère une empreinte cryptographique SHA-256 scellant l'authenticité de l'acte hospitalier.
     */
    public static String computeVerificationHash(UUID tenantId, String docCode, UUID patientId, Instant timestamp, String doctorName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (tenantId != null ? tenantId.toString() : "MEDSCAN_SYS") + ":"
                    + docCode + ":"
                    + (patientId != null ? patientId.toString() : "ANONYMOUS") + ":"
                    + (timestamp != null ? timestamp.toString() : Instant.now().toString()) + ":"
                    + (doctorName != null ? doctorName : "DOCTOR") + ":MEDSCAN_SEAL_V1";
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32).toUpperCase();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
        }
    }
}
