package com.medscan.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Fiche Médicale d'Urgence Autonome Hors-Ligne (MOD-10).
 * Résumé compact optimisé pour le triage d'urgence, le cache hors-ligne
 * et l'affichage par QR Code d'urgence sans connectivité réseau.
 * Intègre une empreinte cryptographique SHA-256 (integrityHash) garantissant l'inviolabilité des données.
 */
public record EmergencyCard(
        UUID patientId,
        String nationalId,
        String fullName,
        String gender,
        String birthDate,
        Integer age,
        String bloodGroup,
        List<String> allergies,
        List<String> chronicConditions,
        String emergencyContact,
        Double weightKg,
        Double heightCm,
        Double bmi,
        String bmiCategory,
        String lastBloodPressure,
        Integer lastHeartRate,
        Double lastOxygenSaturation,
        String lastTriageLevel,
        Instant lastVitalsRecordedAt,
        int activePrescriptionsCount,
        Instant generatedAt,
        String integrityHash
) {
    public EmergencyCard {
        if (allergies == null) allergies = List.of();
        if (chronicConditions == null) chronicConditions = List.of();
        if (bloodGroup == null || bloodGroup.isBlank()) bloodGroup = "Inconnu";
        if (lastTriageLevel == null || lastTriageLevel.isBlank()) lastTriageLevel = "NORMAL";
        if (generatedAt == null) generatedAt = Instant.now();
        if (integrityHash == null || integrityHash.isBlank()) {
            integrityHash = computeChecksum(patientId, nationalId, bloodGroup, allergies, chronicConditions, emergencyContact);
        }
    }

    /**
     * Calcule l'empreinte SHA-256 des données vitales d'urgence pour validation d'intégrité hors-ligne.
     */
    public static String computeChecksum(UUID patientId, String nationalId, String bloodGroup,
                                         List<String> allergies, List<String> chronicConditions,
                                         String emergencyContact) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = (patientId != null ? patientId.toString() : "")
                    + "|" + (nationalId != null ? nationalId : "")
                    + "|" + (bloodGroup != null ? bloodGroup : "")
                    + "|" + String.join(",", allergies != null ? allergies : Collections.emptyList())
                    + "|" + String.join(",", chronicConditions != null ? chronicConditions : Collections.emptyList())
                    + "|" + (emergencyContact != null ? emergencyContact : "");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "sha256-unavailable";
        }
    }

    /**
     * Vérifie que la fiche d'urgence n'a pas été altérée en cache hors-ligne.
     */
    public boolean verifyIntegrity() {
        String expected = computeChecksum(patientId, nationalId, bloodGroup, allergies, chronicConditions, emergencyContact);
        return expected.equalsIgnoreCase(integrityHash);
    }
}
