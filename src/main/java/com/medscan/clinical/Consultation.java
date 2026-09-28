package com.medscan.clinical;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Consultation médicale et notes cliniques d'observation (MOD-03).
 * Intègre l'examen clinique, les constantes au moment de la consultation,
 * l'IMC calculé automatiquement, la classification clinique de la corpulence,
 * et les champs personnalisés dynamiques adaptés à la situation spécifique du patient.
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
        String treatmentPlan,
        Double weightKg,
        Double heightCm,
        Double bmi,
        String bmiCategory,
        Integer systolicBp,
        Integer diastolicBp,
        Integer heartRate,
        Double temperature,
        Double oxygenSaturation,
        Map<String, String> customFields
) {
    public Consultation {
        if (customFields == null) {
            customFields = Collections.emptyMap();
        }
        if (bmi == null && weightKg != null && heightCm != null && heightCm > 0 && weightKg > 0) {
            bmi = Patient.calculateBmi(weightKg, heightCm);
        }
        if (bmiCategory == null && bmi != null) {
            bmiCategory = Patient.classifyBmi(bmi);
        }
    }

    /**
     * Constructeur de compatibilité historique (10 paramètres)
     */
    public Consultation(
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
        this(
                id,
                patientId,
                doctorId,
                doctorName,
                tenantId,
                date,
                chiefComplaint,
                examinationNotes,
                diagnosis,
                treatmentPlan,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap()
        );
    }
}
