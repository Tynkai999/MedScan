package com.medscan.clinical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Constantes vitales enrichies du patient (MOD-03).
 * Intègre les paramètres physiologiques, les alertes de triage,
 * ainsi que le contexte clinique d'urgence (groupe sanguin, allergies, comorbidités, contact d'urgence).
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
        Double heightCm,
        Double bmi,
        Double bloodGlucose,
        Double oxygenSaturation,   // SpO2 en % (ex: 98.0)
        Integer respiratoryRate,   // Fréquence respiratoire / min (ex: 16)
        Integer painScale,         // Échelle EVA 0 à 10
        String bloodGroup,         // Groupe sanguin (ex: "A+", "O+", "B-")
        List<String> allergies,    // Allergies connues (ex: ["Pénicilline", "Arachide"])
        List<String> chronicConditions, // Antécédents majeurs (ex: ["Asthme", "Diabète"])
        String emergencyContact,   // Personne à contacter d'urgence
        String triageLevel,        // "NORMAL", "ATTENTION", "CRITICAL"
        String notes,              // Observations cliniques complémentaires
        String recordedBy,
        String recordedByRole
) {
    public VitalSigns {
        if (allergies == null) {
            allergies = List.of();
        }
        if (chronicConditions == null) {
            chronicConditions = List.of();
        }
        if (bloodGroup == null || bloodGroup.isBlank()) {
            bloodGroup = "Inconnu";
        }
        if (bmi == null && weightKg != null && heightCm != null && heightCm > 0) {
            double heightM = heightCm / 100.0;
            bmi = Math.round((weightKg / (heightM * heightM)) * 10.0) / 10.0;
        }
        if (triageLevel == null || triageLevel.isBlank()) {
            triageLevel = computeTriageLevel(oxygenSaturation, systolicBp, diastolicBp, heartRate, temperature);
        }
    }

    /**
     * Constructeur de compatibilité historique (11 paramètres)
     */
    public VitalSigns(
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
        this(
                id,
                patientId,
                recordedAt,
                systolicBp,
                diastolicBp,
                heartRate,
                temperature,
                weightKg,
                168.0,
                null,
                bloodGlucose,
                98.0,
                16,
                0,
                "Inconnu",
                List.of(),
                List.of(),
                null,
                null,
                null,
                recordedBy,
                recordedByRole
        );
    }

    public static String computeTriageLevel(Double spo2, Integer sys, Integer dia, Integer hr, Double temp) {
        if ((spo2 != null && spo2 < 90.0)
                || (sys != null && (sys >= 180 || sys < 80))
                || (dia != null && dia >= 120)
                || (hr != null && (hr >= 130 || hr < 45))
                || (temp != null && (temp >= 39.5 || temp < 35.0))) {
            return "CRITICAL";
        }
        if ((spo2 != null && spo2 < 95.0)
                || (sys != null && sys >= 140)
                || (dia != null && dia >= 90)
                || (hr != null && (hr >= 100 || hr < 55))
                || (temp != null && temp >= 38.0)) {
            return "ATTENTION";
        }
        return "NORMAL";
    }

    /**
     * Renvoie une nouvelle instance enrichie avec les données de fond du dossier patient si omises.
     */
    public VitalSigns withPatientContext(Patient patient) {
        if (patient == null) return this;

        String bg = ("Inconnu".equalsIgnoreCase(this.bloodGroup) || this.bloodGroup == null)
                ? patient.bloodGroup() : this.bloodGroup;
        List<String> alg = (this.allergies == null || this.allergies.isEmpty())
                ? patient.allergies() : this.allergies;
        List<String> chr = (this.chronicConditions == null || this.chronicConditions.isEmpty())
                ? patient.chronicConditions() : this.chronicConditions;
        String em = (this.emergencyContact == null || this.emergencyContact.isBlank())
                ? patient.emergencyContact() : this.emergencyContact;

        return new VitalSigns(
                this.id,
                this.patientId,
                this.recordedAt,
                this.systolicBp,
                this.diastolicBp,
                this.heartRate,
                this.temperature,
                this.weightKg,
                this.heightCm,
                this.bmi,
                this.bloodGlucose,
                this.oxygenSaturation,
                this.respiratoryRate,
                this.painScale,
                bg,
                alg,
                chr,
                em,
                this.triageLevel,
                this.notes,
                this.recordedBy,
                this.recordedByRole
        );
    }
}
