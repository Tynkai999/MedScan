package com.medscan.epidemiology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;

class EpidemiologyServiceTest {

    private ClinicalService clinicalService;
    private EpidemiologyService epidemiologyService;

    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID DOCTOR_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        epidemiologyService = new EpidemiologyService(clinicalService);
    }

    @Test
    @DisplayName("Déclaration d'un cas épidémiologique à déclaration obligatoire (ex: Paludisme à Ouagadougou)")
    void testReportEpidemiologicalCase() {
        EpidemiologicalCase c = epidemiologyService.reportCase(
                HOSPITAL_TENANT,
                "Centre",
                "Baskuy",
                "MALARIA_FALCIPARUM",
                "Paludisme simple à P. falciparum",
                "LAB_CONFIRMED",
                25,
                "F",
                "UNDER_TREATMENT",
                DOCTOR_ID,
                "dr.traore",
                "DOCTOR"
        );

        assertNotNull(c);
        assertEquals("MALARIA_FALCIPARUM", c.diseaseCode());
        assertEquals("Centre", c.region());
        assertEquals("Baskuy", c.district());
        assertTrue(c.dhis2TrackedEntityId().startsWith("DHIS2-BF-"));
    }

    @Test
    @DisplayName("Génération de la synthèse épidémiologique régionale et analyse des alertes")
    void testEpidemiologicalSummary() {
        Map<String, Object> summary = epidemiologyService.getEpidemiologicalSummary(HOSPITAL_TENANT);
        assertNotNull(summary);
        assertTrue((int) summary.get("totalCases") >= 4);

        @SuppressWarnings("unchecked")
        Map<String, Long> byDisease = (Map<String, Long>) summary.get("casesByDisease");
        assertTrue(byDisease.containsKey("MALARIA_FALCIPARUM"));
        assertTrue(byDisease.containsKey("DENGUE"));
        assertTrue(byDisease.containsKey("MENINGITIS"));
    }

    @Test
    @DisplayName("Export conforme standard international DHIS2 DataValueSet JSON")
    void testExportDhis2DataValueSet() {
        String dhis2Json = epidemiologyService.exportDhis2DataValueSet("2026W42", "ou-bfa-centre-01");
        assertNotNull(dhis2Json);
        assertTrue(dhis2Json.contains("\"dataValues\":"));
        assertTrue(dhis2Json.contains("DE_MALARIA_FALCIPARUM"));
        assertTrue(dhis2Json.contains("2026W42"));
        assertTrue(dhis2Json.contains("ou-bfa-centre-01"));
        assertTrue(dhis2Json.contains("MedScan-DHIS2-Bridge"));
    }
}
