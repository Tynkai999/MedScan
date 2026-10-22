package com.medscan.epidemiology;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;
import com.medscan.security.jwt.JsonHelper;

/**
 * Service de Surveillance Épidémiologique & Déclaration Nationale DHIS2 / OMS (MOD-14).
 * Automatise la veille sanitaire, la détection des dépassements de seuils épidémiques
 * et l'exportation des agrégats vers les plateformes sanitaires nationales.
 */
public class EpidemiologyService {

    private final ClinicalService clinicalService;
    private final List<EpidemiologicalCase> cases = new CopyOnWriteArrayList<>();

    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID DOCTOR_TRAORE_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");

    // Seuils d'alerte épidémique hebdomadaires par pathologie
    private static final Map<String, Integer> EPIDEMIC_WEEKLY_THRESHOLDS = Map.of(
            "MALARIA_FALCIPARUM", 50,
            "DENGUE", 10,
            "MEASLES", 5,
            "MENINGITIS", 5,
            "CHOLERA", 1,
            "COVID19", 20
    );

    public EpidemiologyService(ClinicalService clinicalService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
        initSeedEpidemiologyData();
    }

    public EpidemiologyService() {
        this(new ClinicalService());
    }

    /**
     * Déclare un cas épidémiologique à déclaration obligatoire.
     */
    public EpidemiologicalCase reportCase(UUID tenantId, String region, String district, String diseaseCode,
                                         String diseaseName, String detectionSource, int patientAge,
                                         String patientGender, String outcome, UUID reportedBy,
                                         String actorUsername, String actorRole) {
        if (diseaseCode == null || region == null || district == null) {
            throw new IllegalArgumentException("Pathologie, région et district requis pour la déclaration épidémiologique.");
        }

        String dhis2Id = "DHIS2-BF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        EpidemiologicalCase newCase = new EpidemiologicalCase(
                UUID.randomUUID(),
                tenantId != null ? tenantId : HOSPITAL_TENANT,
                region,
                district,
                diseaseCode.toUpperCase(),
                diseaseName != null ? diseaseName : diseaseCode,
                detectionSource != null ? detectionSource.toUpperCase() : "CLINICAL_SUSPICION",
                patientAge,
                patientGender != null ? patientGender.toUpperCase() : "U",
                outcome != null ? outcome.toUpperCase() : "UNDER_TREATMENT",
                Instant.now(),
                reportedBy != null ? reportedBy : DOCTOR_TRAORE_ID,
                dhis2Id
        );

        cases.add(0, newCase);

        clinicalService.logAudit(reportedBy, actorUsername, actorRole, newCase.tenantId(),
                "EPIDEMIOLOGICAL_CASE_REPORTED", "EpidemiologicalCase", newCase.id().toString(), "SUCCESS",
                "Déclaration de cas épidémiologique: " + newCase.diseaseName() + " (" + newCase.region() + " / " + newCase.district() + ")");

        return newCase;
    }

    public List<EpidemiologicalCase> listCases(UUID tenantId, String diseaseCode, String region) {
        return cases.stream()
                .filter(c -> tenantId == null || c.tenantId().equals(tenantId))
                .filter(c -> diseaseCode == null || diseaseCode.isBlank() || c.diseaseCode().equalsIgnoreCase(diseaseCode))
                .filter(c -> region == null || region.isBlank() || c.region().equalsIgnoreCase(region))
                .sorted(Comparator.comparing(EpidemiologicalCase::reportedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Synthèse épidémiologique régionale et analyse des alertes épidémiques actives.
     */
    public Map<String, Object> getEpidemiologicalSummary(UUID tenantId) {
        List<EpidemiologicalCase> filtered = cases.stream()
                .filter(c -> tenantId == null || c.tenantId().equals(tenantId))
                .toList();

        Map<String, Long> byDisease = filtered.stream()
                .collect(Collectors.groupingBy(EpidemiologicalCase::diseaseCode, Collectors.counting()));

        Map<String, Long> byRegion = filtered.stream()
                .collect(Collectors.groupingBy(EpidemiologicalCase::region, Collectors.counting()));

        Map<String, Long> byOutcome = filtered.stream()
                .collect(Collectors.groupingBy(EpidemiologicalCase::outcome, Collectors.counting()));

        // Détection des dépassements de seuils
        List<Map<String, Object>> activeAlerts = new java.util.ArrayList<>();
        for (Map.Entry<String, Long> entry : byDisease.entrySet()) {
            int threshold = EPIDEMIC_WEEKLY_THRESHOLDS.getOrDefault(entry.getKey(), 20);
            if (entry.getValue() >= threshold) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("diseaseCode", entry.getKey());
                alert.put("caseCount", entry.getValue());
                alert.put("threshold", threshold);
                alert.put("status", "SEUIL_ÉPIDÉMIQUE_DÉPASSÉ");
                activeAlerts.add(alert);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCases", filtered.size());
        summary.put("casesByDisease", byDisease);
        summary.put("casesByRegion", byRegion);
        summary.put("casesByOutcome", byOutcome);
        summary.put("activeEpidemicAlerts", activeAlerts);
        summary.put("generatedAt", Instant.now().toString());

        return summary;
    }

    /**
     * Exporte les indicateurs épidémiologiques au format standard international DHIS2 DataValueSet JSON.
     */
    public String exportDhis2DataValueSet(String period, String orgUnitCode) {
        String p = (period != null && !period.isBlank()) ? period : "2026W42";
        String orgUnit = (orgUnitCode != null && !orgUnitCode.isBlank()) ? orgUnitCode : "ou-bfa-centre-01";

        Map<String, Long> diseaseCounts = cases.stream()
                .collect(Collectors.groupingBy(EpidemiologicalCase::diseaseCode, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"dataValues\": [\n");

        List<String> entries = new java.util.ArrayList<>();
        for (Map.Entry<String, Long> entry : diseaseCounts.entrySet()) {
            entries.add("    {\n" +
                    "      \"dataElement\": \"DE_" + entry.getKey() + "\",\n" +
                    "      \"period\": \"" + JsonHelper.escape(p) + "\",\n" +
                    "      \"orgUnit\": \"" + JsonHelper.escape(orgUnit) + "\",\n" +
                    "      \"value\": \"" + entry.getValue() + "\",\n" +
                    "      \"storedBy\": \"MedScan-DHIS2-Bridge\"\n" +
                    "    }");
        }
        sb.append(String.join(",\n", entries));
        sb.append("\n  ]\n}");

        return sb.toString();
    }

    private void initSeedEpidemiologyData() {
        // Paludisme (Zone Centre / Ouagadougou)
        cases.add(new EpidemiologicalCase(
                UUID.randomUUID(), HOSPITAL_TENANT, "Centre", "Baskuy",
                "MALARIA_FALCIPARUM", "Paludisme à Plasmodium falciparum", "LAB_CONFIRMED",
                32, "F", "UNDER_TREATMENT", Instant.now().minus(2, ChronoUnit.DAYS),
                DOCTOR_TRAORE_ID, "DHIS2-BF-MAL-001"
        ));
        cases.add(new EpidemiologicalCase(
                UUID.randomUUID(), HOSPITAL_TENANT, "Centre", "Bogodogo",
                "MALARIA_FALCIPARUM", "Paludisme grave de l'enfant", "LAB_CONFIRMED",
                4, "M", "HOSPITALIZED", Instant.now().minus(1, ChronoUnit.DAYS),
                DOCTOR_TRAORE_ID, "DHIS2-BF-MAL-002"
        ));

        // Dengue (Hauts-Bassins / Bobo-Dioulasso)
        cases.add(new EpidemiologicalCase(
                UUID.randomUUID(), HOSPITAL_TENANT, "Hauts-Bassins", "Dafra",
                "DENGUE", "Dengue à forme classique", "RAPID_TEST",
                28, "M", "UNDER_TREATMENT", Instant.now().minus(3, ChronoUnit.DAYS),
                DOCTOR_TRAORE_ID, "DHIS2-BF-DEN-001"
        ));

        // Méningite (Sahel / Dori)
        cases.add(new EpidemiologicalCase(
                UUID.randomUUID(), HOSPITAL_TENANT, "Sahel", "Dori",
                "MENINGITIS", "Méningite à Méningocoque A/C", "CLINICAL_SUSPICION",
                14, "F", "HOSPITALIZED", Instant.now().minus(4, ChronoUnit.DAYS),
                DOCTOR_TRAORE_ID, "DHIS2-BF-MEN-001"
        ));
    }
}
