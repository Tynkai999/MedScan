package com.medscan.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;
import com.medscan.imaging.ImagingService;
import com.medscan.imaging.SimulatedAiInferenceEngine;

class DashboardServiceTest {

    private ClinicalService clinicalService;
    private ImagingService imagingService;
    private DashboardService dashboardService;

    private final UUID doctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
    private final UUID hospitalTenant = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private final UUID pharmacyTenant = UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89");
    private final UUID patientUserId = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        imagingService = new ImagingService(clinicalService, new SimulatedAiInferenceEngine());
        dashboardService = new DashboardService(clinicalService, imagingService);
    }

    @Test
    @DisplayName("Statistiques de tableau de bord pour un Médecin (DOCTOR)")
    void testDoctorDashboardStats() {
        ActorDashboardStats stats = dashboardService.getDashboardStats(
                doctorId, "doctor@medscan.org", "DOCTOR", hospitalTenant, "CH_OUAGADOUGOU"
        );

        assertNotNull(stats);
        assertEquals("DOCTOR", stats.role());
        assertEquals("CH_OUAGADOUGOU", stats.tenantName());
        assertNotNull(stats.kpis());
        assertTrue((int) stats.kpis().get("totalPatients") >= 1);
        assertTrue((long) stats.kpis().get("myConsultations") >= 1);
        assertNotNull(stats.chartsData());
        assertFalse(stats.recentActivities().isEmpty());

        String json = DashboardJsonMapper.toJson(stats);
        assertTrue(json.contains("\"role\":\"DOCTOR\""));
        assertTrue(json.contains("\"totalPatients\""));
        assertTrue(json.contains("\"recentActivities\""));
    }

    @Test
    @DisplayName("Statistiques de tableau de bord pour un Pharmacien (PHARMACIST)")
    void testPharmacistDashboardStats() {
        ActorDashboardStats stats = dashboardService.getDashboardStats(
                UUID.randomUUID(), "pharmacist@medscan.org", "PHARMACIST", pharmacyTenant, "PHARMA_CENTRALE"
        );

        assertNotNull(stats);
        assertEquals("PHARMACIST", stats.role());
        assertTrue((long) stats.kpis().get("pendingPrescriptionsToDispense") >= 1);
        assertTrue(stats.chartsData().containsKey("topPrescribedMedications"));

        String json = DashboardJsonMapper.toJson(stats);
        assertTrue(json.contains("\"pendingPrescriptionsToDispense\""));
    }

    @Test
    @DisplayName("Statistiques de tableau de bord pour un Patient (PATIENT)")
    void testPatientDashboardStats() {
        ActorDashboardStats stats = dashboardService.getDashboardStats(
                patientUserId, "patient@medscan.org", "PATIENT", hospitalTenant, "CH_OUAGADOUGOU"
        );

        assertNotNull(stats);
        assertEquals("PATIENT", stats.role());
        assertEquals("A+", stats.kpis().get("bloodGroup"));
        assertNotNull(stats.kpis().get("lastBloodPressure"));
        assertNotNull(stats.kpis().get("allergies"));

        String json = DashboardJsonMapper.toJson(stats);
        assertTrue(json.contains("\"bloodGroup\":\"A+\""));
        assertTrue(json.contains("\"Pénicilline\""));
    }

    @Test
    @DisplayName("Statistiques de tableau de bord pour un Radiologue (RADIOLOGIST)")
    void testRadiologistDashboardStats() {
        ActorDashboardStats stats = dashboardService.getDashboardStats(
                UUID.randomUUID(), "radiologist@medscan.org", "RADIOLOGIST", hospitalTenant, "CH_OUAGADOUGOU"
        );

        assertNotNull(stats);
        assertEquals("RADIOLOGIST", stats.role());
        assertTrue((int) stats.kpis().get("totalImagingStudies") >= 1);
        assertTrue(stats.chartsData().containsKey("modalityDistribution"));
    }
}
