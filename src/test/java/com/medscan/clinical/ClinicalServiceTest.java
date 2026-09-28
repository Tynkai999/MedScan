package com.medscan.clinical;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("Calcul et classification automatique de l'Indice de Masse Corporelle (IMC/BMI)")
    void testBmiCalculationAndClassification() {
        // Maigreur (< 18.5)
        Double bmiThin = Patient.calculateBmi(50.0, 175.0); // 50 / (1.75^2) = 16.3
        assertEquals(16.3, bmiThin);
        assertEquals("Insuffisance pondérale (Maigreur)", Patient.classifyBmi(bmiThin));

        // Corpulence normale (18.5 - 24.9)
        Double bmiNormal = Patient.calculateBmi(70.0, 175.0); // 70 / (1.75^2) = 22.9
        assertEquals(22.9, bmiNormal);
        assertEquals("Corpulence normale", Patient.classifyBmi(bmiNormal));

        // Surpoids (25.0 - 29.9)
        Double bmiOverweight = Patient.calculateBmi(85.0, 175.0); // 85 / (1.75^2) = 27.8
        assertEquals(27.8, bmiOverweight);
        assertEquals("Surpoids", Patient.classifyBmi(bmiOverweight));

        // Obésité modérée (30.0 - 34.9)
        Double bmiObese1 = Patient.calculateBmi(95.0, 172.0); // 95 / (1.72^2) = 32.1
        assertEquals(32.1, bmiObese1);
        assertEquals("Obésité modérée (Classe I)", Patient.classifyBmi(bmiObese1));

        // Obésité sévère (35.0 - 39.9)
        Double bmiObese2 = Patient.calculateBmi(110.0, 172.0); // 110 / (1.72^2) = 37.2
        assertEquals(37.2, bmiObese2);
        assertEquals("Obésité sévère (Classe II)", Patient.classifyBmi(bmiObese2));

        // Obésité morbide (>= 40.0)
        Double bmiObese3 = Patient.calculateBmi(130.0, 172.0); // 130 / (1.72^2) = 43.9
        assertEquals(43.9, bmiObese3);
        assertEquals("Obésité morbide (Classe III)", Patient.classifyBmi(bmiObese3));
    }

    @Test
    @DisplayName("Création d'un patient avec calcul automatique d'âge, IMC et champs personnalisés")
    void testPatientCreationAndCustomFields() {
        String patientJson = """
                {
                    "nationalId": "BFA-2026-990011",
                    "firstName": "Alassane",
                    "lastName": "Sawadogo",
                    "birthDate": "1988-04-12",
                    "gender": "M",
                    "bloodGroup": "O+",
                    "phone": "+226 71 22 33 44",
                    "emergencyContact": "Kadi Sawadogo (+226 70 88 99 00)",
                    "allergies": ["Aspirine"],
                    "chronicConditions": ["Hypercholestérolémie"],
                    "weightKg": 78.5,
                    "heightCm": 178.0,
                    "customFields": {
                        "perimetreAbdominal": "89 cm",
                        "tabagisme": "Non-fumeur",
                        "profession": "Enseignant",
                        "activitePhysique": "3x / semaine"
                    }
                }
                """;

        Patient parsed = ClinicalJsonMapper.parsePatient(patientJson, tenantHospital1);
        Patient saved = service.createPatient(parsed, doctorId, "Dr. Traore", "DOCTOR", tenantHospital1);

        assertNotNull(saved);
        assertEquals("Alassane Sawadogo", saved.fullName());
        assertNotNull(saved.age());
        assertTrue(saved.age() >= 35);
        assertEquals(78.5, saved.weightKg());
        assertEquals(178.0, saved.heightCm());
        assertEquals(24.8, saved.bmi()); // 78.5 / (1.78^2) = 24.77 -> 24.8
        assertEquals("Corpulence normale", saved.bmiCategory());
        assertEquals("89 cm", saved.customFields().get("perimetreAbdominal"));
        assertEquals("Non-fumeur", saved.customFields().get("tabagisme"));
        assertEquals("Enseignant", saved.customFields().get("profession"));

        // Sérialisation JSON vérifiée
        String json = ClinicalJsonMapper.toJson(saved);
        assertTrue(json.contains("\"bmi\":24.8"));
        assertTrue(json.contains("\"bmiCategory\":\"Corpulence normale\""));
        assertTrue(json.contains("\"perimetreAbdominal\":\"89 cm\""));
        assertTrue(json.contains("\"profession\":\"Enseignant\""));
    }

    @Test
    @DisplayName("Mise à jour des informations patient, biométrie et champs personnalisés par le médecin/infirmier")
    void testPatientUpdateByDoctorOrNurse() {
        Patient fatou = service.findPatientById(patientFatouId).orElseThrow();

        String updateJson = """
                {
                    "weightKg": 64.0,
                    "heightCm": 168.0,
                    "bloodGroup": "A+",
                    "customFields": {
                        "statutGrossesse": "Non enceinte",
                        "glycemieAJiun": "0.92 g/L",
                        "tensionHabituelle": "120/80"
                    }
                }
                """;

        Patient merged = ClinicalJsonMapper.parsePatientUpdate(updateJson, fatou);
        Patient updated = service.updatePatient(patientFatouId, merged, doctorId, "Dr. Traore", "DOCTOR", tenantHospital1);

        assertEquals(64.0, updated.weightKg());
        assertEquals(168.0, updated.heightCm());
        assertEquals(22.7, updated.bmi()); // 64 / (1.68^2) = 22.67 -> 22.7
        assertEquals("Corpulence normale", updated.bmiCategory());
        assertEquals("Non enceinte", updated.customFields().get("statutGrossesse"));
        assertEquals("0.92 g/L", updated.customFields().get("glycemieAJiun"));
        assertEquals("Fatou", updated.firstName()); // Nom conservé
    }

    @Test
    @DisplayName("Une consultation calcule automatiquement l'IMC et synchronise le dossier maître du patient")
    void testConsultationAutoCalculatesBmiAndSyncsPatientRecord() {
        // Ibrahim a initialement poids 84.0 kg, taille 178.0 cm -> BMI 26.5
        Patient ibrahimBefore = service.findPatientById(patientIbrahimId).orElseThrow();

        // Le médecin réalise une consultation et renseigne un nouveau poids de 96.0 kg (prise de poids), taille 178.0 cm
        // et ajoute un champ personnalisé "regimeDietetique"
        String consultJson = """
                {
                    "chiefComplaint": "Suivi trimestriel HTA et Diabète avec prise de poids récente",
                    "examinationNotes": "Prise pondérale confirmée. PA 145/92 mmHg.",
                    "diagnosis": "Surpoids aggravant le profil métabolique et tensionnel",
                    "treatmentPlan": "Prescription diététique, réévaluation sous 30 jours",
                    "weightKg": 96.0,
                    "heightCm": 178.0,
                    "systolicBp": 145,
                    "diastolicBp": 92,
                    "heartRate": 80,
                    "temperature": 37.0,
                    "oxygenSaturation": 97.0,
                    "customFields": {
                        "regimeDietetique": "Hypocalorique et hyposodé",
                        "consultationNutritionnelle": "Planifiée pour le 15/10/2026"
                    }
                }
                """;

        Consultation c = ClinicalJsonMapper.parseConsultation(consultJson, patientIbrahimId, doctorId, "Dr. Traore", tenantHospital2);
        Consultation recorded = service.recordConsultation(c, doctorId, "Dr. Traore", "DOCTOR", tenantHospital2);

        // Vérification de l'IMC calculé sur la consultation
        // 96.0 / (1.78^2) = 30.3
        assertNotNull(recorded.bmi());
        assertEquals(30.3, recorded.bmi());
        assertEquals("Obésité modérée (Classe I)", recorded.bmiCategory());
        assertEquals("Hypocalorique et hyposodé", recorded.customFields().get("regimeDietetique"));

        // Vérification que le dossier maître d'Ibrahim a été automatiquement synchronisé !
        Patient ibrahimAfter = service.findPatientById(patientIbrahimId).orElseThrow();
        assertEquals(96.0, ibrahimAfter.weightKg());
        assertEquals(178.0, ibrahimAfter.heightCm());
        assertEquals(30.3, ibrahimAfter.bmi());
        assertEquals("Obésité modérée (Classe I)", ibrahimAfter.bmiCategory());
        assertEquals("Hypocalorique et hyposodé", ibrahimAfter.customFields().get("regimeDietetique"));
        assertEquals("Planifiée pour le 15/10/2026", ibrahimAfter.customFields().get("consultationNutritionnelle"));
    }
}
