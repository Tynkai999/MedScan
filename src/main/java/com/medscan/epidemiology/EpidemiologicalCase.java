package com.medscan.epidemiology;

import java.time.Instant;
import java.util.UUID;

/**
 * Déclaration de cas épidémiologique à déclaration obligatoire (MOD-14).
 * Aligné sur les définitions de cas de l'OMS et les formats de surveillance DHIS2.
 */
public record EpidemiologicalCase(
        UUID id,
        UUID tenantId,
        String region,             // ex: Centre, Hauts-Bassins, Sahel, Boucle du Mouhoun
        String district,           // ex: Baskuy, Bogodogo, Dafra, Dori
        String diseaseCode,        // MALARIA_FALCIPARUM, DENGUE, MEASLES, MENINGITIS, COVID19, CHOLERA, TUBERCULOSIS
        String diseaseName,        // Paludisme grave à P. Falciparum, Dengue hémorragique, Rougeole, etc.
        String detectionSource,    // LAB_CONFIRMED, CLINICAL_SUSPICION, RAPID_TEST
        int patientAge,
        String patientGender,      // M, F
        String outcome,            // UNDER_TREATMENT, RECOVERED, HOSPITALIZED, CRITICAL, DECEASED
        Instant reportedAt,
        UUID reportedBy,
        String dhis2TrackedEntityId
) {}
