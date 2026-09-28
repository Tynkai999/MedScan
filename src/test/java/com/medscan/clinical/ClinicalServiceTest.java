package com.medscan.clinical;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinicalServiceTest {

    private ClinicalService service;
    private final UUID tenantHospital1 = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private final UUID tenantHospital2 = UUID.fromString("f56de30f-c802-42c6-8587-707a8d1a9814");
    private final UUID doctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
    private final UUID patientFatouId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
    private final UUID patientIbrahimId = UUID.fromString("dababb25-8dc9-402c-b527-15d9a4b1f380");

    @BeforeEach
    void setUp() {
        service = new ClinicalService();
    }

    @Test
    @DisplayName("La recherche de patients respecte le cloisonnement tenant")
    void testSearchPatientsIsolatesByTenant() {
        // Médecin dans Hôpital 1
        List<Patient> hospital1Patients = service.searchPatients(tenantHospital1, null, Set.of("DOCTOR"));
        assertEquals(1, hospital1Patients.size());
        assertEquals("Fatou", hospital1Patients.get(0).firstName());

        // Auditeur plateforme
        List<Patient> allPatients = service.searchPatients(tenantHospital1, null, Set.of("AUDITOR"));
        assertTrue(allPatients.size() >= 2);
    }

    @Test
    @DisplayName("Le contrôle d'accès bloque l'accès hors tenant et autorise le Break-Glass d'urgence")
    void testAccessControlAndBreakGlass() {
        // Le médecin d'Hôpital 1 tente d'accéder au dossier d'Ibrahim (Hôpital 2)
        boolean canAccessBefore = service.canAccessPatient(patientIbrahimId, doctorId, tenantHospital1, Set.of("DOCTOR"));
        assertFalse(canAccessBefore, "L'accès à un patient d'un autre tenant doit être refusé par défaut");

        // Déclenchement de la procédure dérogatoire d'urgence vitale
        BreakGlassRecord bg = service.triggerBreakGlass(
                patientIbrahimId,
                doctorId,
                "Dr. Seydou Traore",
                tenantHospital1,
                "Patient comateux reçu aux urgences vitales sans accompagnant"
        );
        assertNotNull(bg);
        assertEquals("CRITICAL_EMERGENCY", bg.emergencyLevel());

        // L'accès est désormais accordé
        boolean canAccessAfter = service.canAccessPatient(patientIbrahimId, doctorId, tenantHospital1, Set.of("DOCTOR"));
        assertTrue(canAccessAfter, "Le Break-Glass doit déverrouiller l'accès pour le praticien en urgence");

        // Vérification de la trace d'audit
        List<AuditEvent> audits = service.getAuditLogs(tenantHospital1, true, 10);
        assertTrue(audits.stream().anyMatch(a -> "BREAK_GLASS_TRIGGERED".equals(a.action())));
    }

    @Test
    @DisplayName("Cycle complet d'ordonnance : émission, consultation, et dispensation unique par le pharmacien")
    void testPrescriptionDispensationCycle() {
        // Consultation de l'ordonnance existante RX-2026-0042
        Prescription rx = service.findPrescriptionByCode("RX-2026-0042").orElseThrow();
        assertEquals("ISSUED", rx.status());

        // Dispensation par le pharmacien
        UUID pharmacyTenant = UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89");
        UUID pharmacistId = UUID.fromString("62cac0f6-d219-43ca-ab33-2571df0c7630");
        Prescription dispensed = service.dispensePrescription("RX-2026-0042", "Dr. Ousmane Zongo (Pharmacien)", pharmacyTenant, pharmacistId);

        assertEquals("DISPENSED", dispensed.status());
        assertEquals("Dr. Ousmane Zongo (Pharmacien)", dispensed.dispensedBy());
        assertNotNull(dispensed.dispensedAt());

        // Tentative de re-dispensation (doit lever une exception anti-fraude)
        assertThrows(IllegalStateException.class, () -> {
            service.dispensePrescription("RX-2026-0042", "Dr. Ousmane Zongo (Pharmacien)", pharmacyTenant, pharmacistId);
        });
    }

    @Test
    @DisplayName("Enregistrement de constantes vitales et de consultation avec audit")
    void testVitalsAndConsultationRecording() {
        VitalSigns v = new VitalSigns(
                UUID.randomUUID(),
                patientFatouId,
                Instant.now(),
                135,
                88,
                82,
                38.2,
                63.0,
                1.05,
                "Inf. Awa Kaboré",
                "NURSE"
        );
        service.recordVitals(v, UUID.randomUUID(), "awa@medscan.org", "NURSE", tenantHospital1);

        List<VitalSigns> vitalsList = service.getVitalSigns(patientFatouId);
        assertEquals(2, vitalsList.size());
        assertEquals(135, vitalsList.get(0).systolicBp());
        assertEquals("A+", vitalsList.get(0).bloodGroup());
        assertTrue(vitalsList.get(0).allergies().contains("Pénicilline"));
        assertEquals("Asthme léger", vitalsList.get(0).chronicConditions().get(0));
        assertEquals("ATTENTION", vitalsList.get(0).triageLevel()); // Température 38.2°C déclenche ATTENTION

        // Baseline Fatou
        VitalSigns baseline = vitalsList.get(1);
        assertEquals(98.5, baseline.oxygenSaturation());
        assertEquals(22.1, baseline.bmi());
        assertEquals("NORMAL", baseline.triageLevel());
        assertEquals("Moussa Ouedraogo (+226 76 11 22 33)", baseline.emergencyContact());

        String json = ClinicalJsonMapper.toJson(vitalsList.get(0));
        assertTrue(json.contains("\"bloodGroup\":\"A+\""));
        assertTrue(json.contains("\"Pénicilline\""));
        assertTrue(json.contains("\"triageLevel\":\"ATTENTION\""));

        Consultation c = new Consultation(
                UUID.randomUUID(),
                patientFatouId,
                doctorId,
                "Dr. Seydou Traore",
                tenantHospital1,
                Instant.now(),
                "Fièvre modérée et toux",
                "Fébrile à 38.2°C, pharyngite congestive",
                "Syndrome grippal saisonnier",
                "Hydratation, repos et antipyrétiques"
        );
        service.recordConsultation(c, doctorId, "Dr. Seydou Traore", "DOCTOR", tenantHospital1);

        List<Consultation> consultations = service.getConsultations(patientFatouId);
        assertEquals(2, consultations.size());
        assertEquals("Syndrome grippal saisonnier", consultations.get(0).diagnosis());
    }
}
