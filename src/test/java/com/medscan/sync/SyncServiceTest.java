package com.medscan.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;
import com.medscan.clinical.VitalSigns;

class SyncServiceTest {

    private ClinicalService clinicalService;
    private SyncService syncService;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"); // Fatou Ouedraogo
    private static final UUID DOCTOR_ID = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");
    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID PATIENT_USER_ID = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        syncService = new SyncService(clinicalService);
    }

    @Test
    @DisplayName("Génération et vérification d'intégrité de la Fiche Médicale d'Urgence autonome")
    void testEmergencyCardGenerationAndTamperIntegrity() {
        EmergencyCard card = syncService.generateEmergencyCard(
                PATIENT_ID,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT
        );

        assertNotNull(card);
        assertEquals(PATIENT_ID, card.patientId());
        assertEquals("Fatou Ouedraogo", card.fullName());
        assertEquals("A+", card.bloodGroup());
        assertTrue(card.allergies().contains("Pénicilline"));
        assertTrue(card.chronicConditions().contains("Asthme léger"));
        assertNotNull(card.emergencyContact());
        assertNotNull(card.lastBloodPressure());
        assertNotNull(card.integrityHash());

        // L'empreinte cryptographique SHA-256 est valide
        assertTrue(card.verifyIntegrity(), "L'empreinte SHA-256 de la fiche d'urgence doit être valide.");

        // Simulation d'une altération malveillante en cache hors-ligne
        EmergencyCard tampered = new EmergencyCard(
                card.patientId(),
                card.nationalId(),
                card.fullName(),
                card.gender(),
                card.birthDate(),
                card.age(),
                "AB-", // Groupe sanguin modifié illicitement !
                card.allergies(),
                card.chronicConditions(),
                card.emergencyContact(),
                card.weightKg(),
                card.heightCm(),
                card.bmi(),
                card.bmiCategory(),
                card.lastBloodPressure(),
                card.lastHeartRate(),
                card.lastOxygenSaturation(),
                card.lastTriageLevel(),
                card.lastVitalsRecordedAt(),
                card.activePrescriptionsCount(),
                card.generatedAt(),
                card.integrityHash() // Ancienne empreinte
        );

        assertFalse(tampered.verifyIntegrity(), "La fiche d'urgence altérée doit être rejetée lors du contrôle d'intégrité.");
    }

    @Test
    @DisplayName("Téléchargement du coffre hors-ligne complet pour le personnel médical")
    void testGenerateOfflineVaultFull() {
        SyncPackage pkg = syncService.generateOfflineVault(
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                null,
                null
        );

        assertNotNull(pkg);
        assertTrue(pkg.totalRecords() > 0);
        assertFalse(pkg.patients().isEmpty());
        assertFalse(pkg.vitalSigns().isEmpty());
        assertFalse(pkg.emergencyCards().isEmpty());
        assertNull(pkg.sinceTimestamp());
    }

    @Test
    @DisplayName("Filtrage différentiel (Delta-Sync) avec curseur temporel")
    void testDeltaSyncFiltering() {
        // Delta dans le futur -> aucun enregistrement
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        SyncPackage futureDelta = syncService.generateOfflineVault(
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                null,
                future
        );

        assertNotNull(futureDelta);
        assertEquals(0, futureDelta.totalRecords());
        assertTrue(futureDelta.patients().isEmpty());
        assertTrue(futureDelta.vitalSigns().isEmpty());
        assertTrue(futureDelta.consultations().isEmpty());

        // Delta dans le passé -> l'ensemble des données récentes
        Instant past = Instant.now().minus(30, ChronoUnit.DAYS);
        SyncPackage pastDelta = syncService.generateOfflineVault(
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                null,
                past
        );

        assertNotNull(pastDelta);
        assertTrue(pastDelta.totalRecords() > 0);
    }

    @Test
    @DisplayName("Sécurité RBAC : un patient ne peut pas synchroniser le dossier d'un tiers")
    void testPatientRbacRestriction() {
        UUID otherPatientId = UUID.fromString("7b43f9a2-45e1-4c12-8d9e-56a87b23c912");

        assertThrows(SecurityException.class, () -> {
            syncService.generateOfflineVault(
                    PATIENT_USER_ID,
                    "aminata.traore",
                    "PATIENT",
                    HOSPITAL_TENANT,
                    otherPatientId, // Tente de synchroniser un autre patient
                    null
            );
        });
    }

    @Test
    @DisplayName("Ingestion de lot hors-ligne strictement idempotente (déduplication)")
    void testIdempotentPushBatch() {
        UUID opIdVitals = UUID.randomUUID();
        UUID opIdConsult = UUID.randomUUID();
        Instant measurementTime = Instant.now().minus(2, ChronoUnit.HOURS);

        String vitalsPayload = "{\"patientId\":\"" + PATIENT_ID + "\",\"systolicBp\":128,\"diastolicBp\":84,\"heartRate\":76,\"temperature\":37.2,\"weightKg\":68.5,\"heightCm\":172.0,\"oxygenSaturation\":99.0,\"notes\":\"Consultation dispensaire de brousse\"}";
        String consultPayload = "{\"patientId\":\"" + PATIENT_ID + "\",\"chiefComplaint\":\"Toux sèche persistante\",\"diagnosis\":\"Bronchite aiguë\",\"treatmentPlan\":\"Hydratation, paracétamol et repos 5j\"}";

        SyncBatchRequest request = new SyncBatchRequest(
                UUID.randomUUID(),
                "mobile-android-triage-01",
                Instant.now(),
                List.of(
                        new SyncOperation(opIdVitals, "VITAL_SIGNS", "CREATE", measurementTime, vitalsPayload),
                        new SyncOperation(opIdConsult, "CONSULTATION", "CREATE", measurementTime, consultPayload)
                ),
                null
        );

        int initialVitalsCount = clinicalService.getVitalSigns(PATIENT_ID).size();
        int initialConsultCount = clinicalService.getConsultations(PATIENT_ID).size();

        // 1er envoi : les deux opérations doivent être nouvellement appliquées
        SyncBatchResponse firstResp = syncService.processBatchPush(
                request,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT
        );

        assertEquals(2, firstResp.totalOperations());
        assertEquals(2, firstResp.appliedCount());
        assertEquals(0, firstResp.duplicateCount());
        assertEquals(0, firstResp.errorCount());

        assertEquals(initialVitalsCount + 1, clinicalService.getVitalSigns(PATIENT_ID).size());
        assertEquals(initialConsultCount + 1, clinicalService.getConsultations(PATIENT_ID).size());

        // Constantes créées avec l'horodatage terrain exact
        VitalSigns recorded = clinicalService.getVitalSigns(PATIENT_ID).get(0);
        assertEquals(measurementTime, recorded.recordedAt());
        assertEquals(128, recorded.systolicBp());

        // 2e envoi (rejeu réseau / reconnexion) : doit être détecté comme doublon idempotent sans dupliquer les données
        SyncBatchResponse retryResp = syncService.processBatchPush(
                request,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT
        );

        assertEquals(2, retryResp.totalOperations());
        assertEquals(0, retryResp.appliedCount());
        assertEquals(2, retryResp.duplicateCount());
        assertEquals(0, retryResp.errorCount());

        // Aucun enregistrement supplémentaire n'a été inséré !
        assertEquals(initialVitalsCount + 1, clinicalService.getVitalSigns(PATIENT_ID).size());
        assertEquals(initialConsultCount + 1, clinicalService.getConsultations(PATIENT_ID).size());
    }

    @Test
    @DisplayName("Synchronisation différentielle bidirectionnelle dans un seul aller-retour HTTP (Push + Pull Delta)")
    void testPushWithDeltaRoundTrip() {
        UUID opId = UUID.randomUUID();
        String payload = "{\"patientId\":\"" + PATIENT_ID + "\",\"systolicBp\":120,\"diastolicBp\":80,\"heartRate\":72,\"temperature\":36.8}";

        Instant pullCursor = Instant.now().minus(10, ChronoUnit.MINUTES);

        SyncBatchRequest request = new SyncBatchRequest(
                UUID.randomUUID(),
                "mobile-android-triage-02",
                Instant.now(),
                List.of(new SyncOperation(opId, "VITAL_SIGNS", "CREATE", Instant.now(), payload)),
                pullCursor // Requête un delta en retour
        );

        SyncBatchResponse resp = syncService.processBatchPush(
                request,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT
        );

        assertEquals(1, resp.appliedCount());
        assertNotNull(resp.delta(), "Le delta de synchronisation doit être fourni en retour d'ingestion.");
        assertTrue(resp.delta().totalRecords() > 0);
    }

    @Test
    @DisplayName("Sérialisation et désérialisation JSON sans dépendance externe")
    void testJsonMapperNominal() {
        EmergencyCard card = syncService.generateEmergencyCard(PATIENT_ID, DOCTOR_ID, "dr.sawadogo", "DOCTOR", HOSPITAL_TENANT);
        String cardJson = SyncJsonMapper.toJson(card);
        assertNotNull(cardJson);
        assertTrue(cardJson.contains("\"fullName\":\"Fatou Ouedraogo\""));
        assertTrue(cardJson.contains("\"integrityHash\":"));

        String batchReqJson = "{\"batchId\":\"" + UUID.randomUUID() + "\",\"deviceId\":\"unit-test\",\"operations\":[{\"operationId\":\"" + UUID.randomUUID() + "\",\"entityType\":\"VITAL_SIGNS\",\"action\":\"CREATE\",\"payload\":{\"patientId\":\"" + PATIENT_ID + "\",\"systolicBp\":130}}]}";
        SyncBatchRequest parsed = SyncJsonMapper.parseSyncBatchRequest(batchReqJson);
        assertNotNull(parsed);
        assertEquals("unit-test", parsed.deviceId());
        assertEquals(1, parsed.operations().size());
        assertEquals("VITAL_SIGNS", parsed.operations().get(0).entityType());
    }
}
