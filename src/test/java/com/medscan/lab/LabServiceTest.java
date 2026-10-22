package com.medscan.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;

class LabServiceTest {

    private ClinicalService clinicalService;
    private LabService labService;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"); // Fatou Ouedraogo
    private static final UUID DOCTOR_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");  // Dr Seydou Traore
    private static final UUID TECH_ID = UUID.fromString("1e4a2c5b-8d3f-4e6a-9b7c-0a1d2e3f4a5b");    // Tech Ousmane Sanou
    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        labService = new LabService(clinicalService);
    }

    @Test
    @DisplayName("Cycle complet d'analyse de laboratoire : Prescription, Prélèvement et Validation des résultats")
    void testLabOrderFullWorkflow() {
        // 1. Prescription par le médecin
        LabOrder order = labService.createLabOrder(
                PATIENT_ID,
                "HEMATOLOGY",
                "Numération Formule Sanguine (NFS)",
                "URGENT",
                "Suspicion anémie fébrile",
                DOCTOR_ID,
                "Dr. Seydou Traore",
                HOSPITAL_TENANT
        );

        assertNotNull(order);
        assertEquals("ORDERED", order.status());
        assertTrue(order.orderCode().startsWith("LAB-2026-"));

        // 2. Prélèvement de l'échantillon
        LabOrder collected = labService.collectSample(order.id(), TECH_ID, "tech.sanou", "LAB_TECHNICIAN", HOSPITAL_TENANT);
        assertEquals("SAMPLE_COLLECTED", collected.status());
        assertNotNull(collected.sampleCollectedAt());

        // 3. Saisie et validation des résultats par le technicien
        List<LabResultItem> results = List.of(
                LabResultItem.ofNumeric("HEMOGLOBIN", "Hémoglobine", "718-7",
                        6.5, "g/dL", 12.0, 16.0, 7.0, 20.0, "Anémie sévère critique"),
                LabResultItem.ofNumeric("LEUKOCYTES", "Globules Blancs", "6690-2",
                        14500.0, "/mm3", 4000.0, 10000.0, 1000.0, 30000.0, "Hyperleucocytose modérée")
        );

        LabOrder validated = labService.validateResults(order.id(), TECH_ID, "Tech. Ousmane Sanou", results, HOSPITAL_TENANT);
        assertEquals("VALIDATED", validated.status());
        assertTrue(validated.hasCriticalAlert(), "L'anémie à 6.5 g/dL doit déclencher une alerte critique.");
        assertEquals(2, validated.results().size());
    }

    @Test
    @DisplayName("Export conforme HL7 FHIR R4 d'une analyse de laboratoire (catégorie laboratory & LOINC)")
    void testExportFhirLaboratoryBundle() {
        LabOrder order = labService.findByCode("LAB-2026-0042").orElseThrow();
        String fhirBundle = labService.toFhirLaboratoryBundle(order, "https://medscan-sluw.onrender.com/medscan/api/fhir/r4");

        assertNotNull(fhirBundle);
        assertTrue(fhirBundle.contains("\"resourceType\": \"Bundle\""));
        assertTrue(fhirBundle.contains("\"type\": \"searchset\""));
        assertTrue(fhirBundle.contains("\"code\": \"laboratory\""));
        assertTrue(fhirBundle.contains("50311-0") || fhirBundle.contains("718-7")); // Codes LOINC
        assertTrue(fhirBundle.contains("Fatou Ouedraogo"));
    }

    @Test
    @DisplayName("Filtrage des analyses en attente pour le tableau de bord du laboratoire")
    void testPendingLabOrdersFilter() {
        List<LabOrder> pending = labService.listPendingOrders(HOSPITAL_TENANT);
        assertNotNull(pending);
        // Les analyses seed 0042 et 0043 sont VALIDATED, donc pending ne doit pas les contenir
        assertFalse(pending.stream().anyMatch(o -> "VALIDATED".equals(o.status())));
    }
}
