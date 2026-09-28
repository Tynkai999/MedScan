 package com.medscan.clinical;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entité Patient du Dossier Médical Partagé (MOD-03).
 * Respecte l'isolation multi-tenant stricte et le Master Patient Index (MPI).
 * Intègre les données biométriques (taille, poids, calcul automatique IMC et classification),
 * les données d'urgence et les attributs personnalisés dynamiques (customFields).
 */
public record Patient(
        UUID id,
        String nationalId,
        String firstName,
        String lastName,
        String birthDate,
        Integer age,
        String gender,
        String bloodGroup,
        String phone,
        String emergencyContact,
        List<String> allergies,
        List<String> chronicConditions,
        Double weightKg,
        Double heightCm,
        Double bmi,
        String bmiCategory,
        Map<String, String> customFields,
        UUID tenantId,
        UUID linkedUserId,
        Instant createdAt,
        Instant updatedAt
) {
    public Patient {
        if (allergies == null) {
            allergies = List.of();
        }
        if (chronicConditions == null) {
            chronicConditions = List.of();
        }
        if (customFields == null) {
            customFields = Collections.emptyMap();
        }
        if (bloodGroup == null || bloodGroup.isBlank()) {
            bloodGroup = "Inconnu";
        }
        if (age == null && birthDate != null && !birthDate.isBlank()) {
            age = computeAge(birthDate);
        }
        if (bmi == null && weightKg != null && heightCm != null && heightCm > 0 && weightKg > 0) {
            bmi = calculateBmi(weightKg, heightCm);
        }
        if (bmiCategory == null && bmi != null) {
            bmiCategory = classifyBmi(bmi);
        }
        if (updatedAt == null) {
            updatedAt = createdAt != null ? createdAt : Instant.now();
        }
    }

    /**
     * Constructeur de compatibilité historique (14 arguments)
     */
    public Patient(
            UUID id,
            String nationalId,
            String firstName,
            String lastName,
            String birthDate,
            String gender,
            String bloodGroup,
            String phone,
            String emergencyContact,
            List<String> allergies,
            List<String> chronicConditions,
            UUID tenantId,
            UUID linkedUserId,
            Instant createdAt
    ) {
        this(
                id,
                nationalId,
                firstName,
                lastName,
                birthDate,
                computeAge(birthDate),
                gender,
                bloodGroup,
                phone,
                emergencyContact,
                allergies,
                chronicConditions,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                tenantId,
                linkedUserId,
                createdAt,
                createdAt
        );
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public static Double calculateBmi(Double weightKg, Double heightCm) {
        if (weightKg == null || heightCm == null || heightCm <= 0 || weightKg <= 0) {
            return null;
        }
        double heightM = heightCm / 100.0;
        return Math.round((weightKg / (heightM * heightM)) * 10.0) / 10.0;
    }

    public static String classifyBmi(Double bmi) {
        if (bmi == null || bmi <= 0.0) {
            return "Indéterminé";
        }
        if (bmi < 18.5) {
            return "Insuffisance pondérale (Maigreur)";
        }
        if (bmi < 25.0) {
            return "Corpulence normale";
        }
        if (bmi < 30.0) {
            return "Surpoids";
        }
        if (bmi < 35.0) {
            return "Obésité modérée (Classe I)";
        }
        if (bmi < 40.0) {
            return "Obésité sévère (Classe II)";
        }
        return "Obésité morbide (Classe III)";
    }

    public static Integer computeAge(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return null;
        }
        try {
            LocalDate birth = LocalDate.parse(birthDate.trim());
            LocalDate now = LocalDate.now();
            if (birth.isAfter(now)) {
                return 0;
            }
            return Period.between(birth, now).getYears();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fusionne les mises à jour apportées par le médecin ou l'infirmier.
     */
    public Patient mergeUpdates(
            String newFirstName,
            String newLastName,
            String newBirthDate,
            Integer newAge,
            String newGender,
            String newBloodGroup,
            String newPhone,
            String newEmergencyContact,
            List<String> newAllergies,
            List<String> newChronicConditions,
            Double newWeightKg,
            Double newHeightCm,
            Map<String, String> newCustomFields
    ) {
        String fn = (newFirstName != null && !newFirstName.isBlank()) ? newFirstName : this.firstName;
        String ln = (newLastName != null && !newLastName.isBlank()) ? newLastName : this.lastName;
        String bd = (newBirthDate != null && !newBirthDate.isBlank()) ? newBirthDate : this.birthDate;
        Integer ag = (newAge != null) ? newAge : (bd != null ? computeAge(bd) : this.age);
        String gd = (newGender != null && !newGender.isBlank()) ? newGender : this.gender;
        String bg = (newBloodGroup != null && !newBloodGroup.isBlank() && !"Inconnu".equalsIgnoreCase(newBloodGroup))
                ? newBloodGroup : this.bloodGroup;
        String ph = (newPhone != null && !newPhone.isBlank()) ? newPhone : this.phone;
        String ec = (newEmergencyContact != null && !newEmergencyContact.isBlank()) ? newEmergencyContact : this.emergencyContact;
        List<String> alg = (newAllergies != null && !newAllergies.isEmpty()) ? newAllergies : this.allergies;
        List<String> chr = (newChronicConditions != null && !newChronicConditions.isEmpty()) ? newChronicConditions : this.chronicConditions;
        Double wt = (newWeightKg != null && newWeightKg > 0) ? newWeightKg : this.weightKg;
        Double ht = (newHeightCm != null && newHeightCm > 0) ? newHeightCm : this.heightCm;

        Map<String, String> mergedCustom = new java.util.LinkedHashMap<>(this.customFields);
        if (newCustomFields != null && !newCustomFields.isEmpty()) {
            mergedCustom.putAll(newCustomFields);
        }

        Double newBmi = calculateBmi(wt, ht);
        String newBmiCat = classifyBmi(newBmi);

        return new Patient(
                this.id,
                this.nationalId,
                fn,
                ln,
                bd,
                ag,
                gd,
                bg,
                ph,
                ec,
                alg,
                chr,
                wt,
                ht,
                newBmi,
                newBmiCat,
                mergedCustom,
                this.tenantId,
                this.linkedUserId,
                this.createdAt,
                Instant.now()
        );
    }
}
