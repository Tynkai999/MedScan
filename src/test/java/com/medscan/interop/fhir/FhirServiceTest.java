package com.medscan.interop.fhir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.AuditEvent;
import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;

class FhirServiceTest {

    private ClinicalService clinicalService;
    private FhirService fhirService;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"); // Fatou Ouedraogo
    private static final UUID DOCTOR_ID = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");
    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final String BASE_URL = "https://medscan-sluw.onrender.com/medscan/api";

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        fhirService = new FhirService(clinicalService);
    }

    @Test
    @DisplayName("Génération conforme du CapabilityStatement HL7 FHIR R4 (/fhir/r4/metadata)")
    void testCapabilityStatement() {
        String metadata = fhirService.getCapabilityStatement(BASE_URL);
        assertNotNull(metadata);
        assertTrue(metadata.contains("\"resourceType\": \"CapabilityStatement\""));
        assertTrue(metadata.contains("\"fhirVersion\": \"4.0.1\""));
        assertTrue(metadata.contains("\"status\": \"active\""));
        assertTrue(metadata.contains("\"Patient\""));
        assertTrue(metadata.contains("\"Observation\""));
        assertTrue(metadata.contains("\"MedicationRequest\""));
        assertTrue(metadata.contains("\"Bundle\""));
        assertTrue(metadata.contains("http://terminology.hl7.org/CodeSystem/restful-security-service"));
    }

    @Test
    @DisplayName("Lecture FHIR R4 Patient avec extensions cliniques et sécurité RBAC")
    void testGetPatientSuccess() {
        String patientJson = fhirService.getPatient(
                PATIENT_ID,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                BASE_URL
        );

        assertNotNull(patientJson);
        assertTrue(patientJson.contains("\"resourceType\": \"Patient\""));
        assertTrue(patientJson.contains("\"id\": \"" + PATIENT_ID + "\""));
        assertTrue(patientJson.contains("\"family\": \"Ouedraogo\""));
        assertTrue(patientJson.contains("\"given\": [\"Fatou\"]"));
        assertTrue(patientJson.contains("BFA-2026-008412"));
        assertTrue(patientJson.contains("allergies"));
        assertTrue(patientJson.contains("Pénicilline"));

        // Vérification de la traçabilité audit DPO
        List<AuditEvent> audits = clinicalService.getAuditLogs(HOSPITAL_TENANT, true, 50);
        boolean foundAudit = audits.stream()
                .anyMatch(a -> "FHIR_PATIENT_READ".equals(a.action()) && PATIENT_ID.toString().equals(a.resourceId()));
        assertTrue(foundAudit, "L'accès FHIR R4 doit être tracé dans le journal d'audit.");
    }

    @Test
    @DisplayName("Rejet de l'accès FHIR Patient pour acteur non autorisé (Zero Trust)")
    void testGetPatientAccessDenied() {
        UUID unauthorizedUser = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();

        assertThrows(SecurityException.class, () -> {
            fhirService.getPatient(
                    PATIENT_ID,
                    unauthorizedUser,
                    "intruder",
                    "PATIENT", // Patient essayant d'accéder à un autre dossier sans droit
                    otherTenant,
                    BASE_URL
            );
        });
    }

    @Test
    @DisplayName("Recherche FHIR Patient par identifiant national ou nom (Searchset Bundle)")
    void testSearchPatients() {
        String searchBundle = fhirService.searchPatients(
                "BFA-2026-008412",
                null,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                BASE_URL + "/fhir/r4/Patient?identifier=BFA-2026-008412"
        );

        assertNotNull(searchBundle);
        assertTrue(searchBundle.contains("\"resourceType\": \"Bundle\""));
        assertTrue(searchBundle.contains("\"type\": \"searchset\""));
        assertTrue(searchBundle.contains("\"total\": 1"));
        assertTrue(searchBundle.contains("Fatou"));
    }

    @Test
    @DisplayName("Création d'un Patient conforme FHIR R4 avec ingestion des extensions")
    void testCreatePatientFhir() {
        String fhirInput = """
                {
                  "resourceType": "Patient",
                  "identifier": [
                    { "system": "https://sante.gov.bf/identifiers/national-id", "value": "BF-NAT-2001-9999" }
                  ],
                  "name": [
                    { "use": "official", "family": "Kaboré", "given": ["Amadou"] }
                  ],
                  "telecom": [
                    { "system": "phone", "value": "+226 70 99 88 77" }
                  ],
                  "gender": "male",
                  "birthDate": "2001-05-15",
                  "extension": [
                    { "url": "http://medscan.org/fhir/StructureDefinition/blood-group", "valueString": "O+" },
                    { "url": "http://medscan.org/fhir/StructureDefinition/allergies", "valueString": "Aspirine" }
                  ]
                }
                """;

        String createdJson = fhirService.createPatient(
                fhirInput,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                BASE_URL
        );

        assertNotNull(createdJson);
        assertTrue(createdJson.contains("\"resourceType\": \"Patient\""));
        assertTrue(createdJson.contains("Kaboré"));
        assertTrue(createdJson.contains("Amadou"));
        assertTrue(createdJson.contains("BF-NAT-2001-9999"));
        assertTrue(createdJson.contains("O+"));

        // Vérification de persistance dans le service clinique
        Patient retrieved = clinicalService.searchPatients(HOSPITAL_TENANT, "Kaboré", java.util.Set.of("DOCTOR"))
                .stream().findFirst().orElse(null);
        assertNotNull(retrieved);
        assertEquals("Amadou Kaboré", retrieved.fullName());
        assertEquals("O+", retrieved.bloodGroup());
        assertTrue(retrieved.allergies().contains("Aspirine"));
    }

    @Test
    @DisplayName("Recherche FHIR Observations codifiées LOINC & unités UCUM")
    void testSearchObservationsLoinc() {
        String obsBundle = fhirService.searchObservations(
                PATIENT_ID,
                null,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                BASE_URL + "/fhir/r4/Observation?patient=" + PATIENT_ID
        );

        assertNotNull(obsBundle);
        assertTrue(obsBundle.contains("\"resourceType\": \"Bundle\""));
        assertTrue(obsBundle.contains("\"type\": \"searchset\""));

        // Vérification de la codification LOINC
        assertTrue(obsBundle.contains(FhirConstants.LOINC_BP_PANEL)); // 85354-9 (Blood Pressure Panel)
        assertTrue(obsBundle.contains(FhirConstants.LOINC_BP_SYSTOLIC)); // 8480-6
        assertTrue(obsBundle.contains(FhirConstants.LOINC_BP_DIASTOLIC)); // 8462-4
        assertTrue(obsBundle.contains(FhirConstants.LOINC_HEART_RATE)); // 8867-4
        assertTrue(obsBundle.contains(FhirConstants.LOINC_BODY_TEMP)); // 8310-5
        assertTrue(obsBundle.contains(FhirConstants.LOINC_BMI)); // 39156-5
        assertTrue(obsBundle.contains(FhirConstants.LOINC_SPO2)); // 2708-6

        // Vérification des unités UCUM
        assertTrue(obsBundle.contains("mm[Hg]"));
        assertTrue(obsBundle.contains("/min"));
        assertTrue(obsBundle.contains("Cel"));
        assertTrue(obsBundle.contains("kg/m2"));
    }

    @Test
    @DisplayName("Filtrage d'observations FHIR par code LOINC spécifique (ex: Fréquence Cardiaque)")
    void testSearchObservationsFilteringByLoincCode() {
        String hrBundle = fhirService.searchObservations(
                PATIENT_ID,
                FhirConstants.LOINC_HEART_RATE,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                BASE_URL + "/fhir/r4/Observation?patient=" + PATIENT_ID + "&code=" + FhirConstants.LOINC_HEART_RATE
        );

        assertNotNull(hrBundle);
        assertTrue(hrBundle.contains(FhirConstants.LOINC_HEART_RATE));
        assertTrue(hrBundle.contains("Fréquence Cardiaque") || hrBundle.contains("Heart rate"));
    }

    @Test
    @DisplayName("Consultation de prescription sous forme FHIR R4 MedicationRequest")
    void testGetMedicationRequest() {
        var prescriptions = clinicalService.getPrescriptionsForPatient(PATIENT_ID);
        assertFalse(prescriptions.isEmpty(), "Le patient de test doit posséder au moins une ordonnance.");

        var rx = prescriptions.get(0);
        String mrJson = fhirService.getMedicationRequest(
                rx.prescriptionCode(),
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                BASE_URL
        );

        assertNotNull(mrJson);
        assertTrue(mrJson.contains("\"resourceType\": \"MedicationRequest\""));
        assertTrue(mrJson.contains(rx.prescriptionCode()));
        assertTrue(mrJson.contains("Amoxicilline") || mrJson.contains("Salbutamol") || mrJson.contains("Paracétamol"));
        assertTrue(mrJson.contains("dosageInstruction"));
        assertTrue(mrJson.contains("\"intent\": \"order\""));
    }

    @Test
    @DisplayName("Export complet du Dossier Médical Partagé sous forme de Collection Bundle FHIR R4")
    void testExportPatientRecordAsBundle() {
        String fullBundle = fhirService.exportPatientRecordAsBundle(
                PATIENT_ID,
                DOCTOR_ID,
                "dr.sawadogo",
                "DOCTOR",
                HOSPITAL_TENANT,
                BASE_URL + "/fhir/r4/Bundle?patient=" + PATIENT_ID
        );

        assertNotNull(fullBundle);
        assertTrue(fullBundle.contains("\"resourceType\": \"Bundle\""));
        assertTrue(fullBundle.contains("\"type\": \"collection\""));
        assertTrue(fullBundle.contains("\"resourceType\": \"Patient\""));
        assertTrue(fullBundle.contains("\"resourceType\": \"Observation\""));
        assertTrue(fullBundle.contains("\"resourceType\": \"MedicationRequest\""));
        assertTrue(fullBundle.contains("Fatou Ouedraogo"));

        // Audit d'export complet tracé
        List<AuditEvent> audits = clinicalService.getAuditLogs(HOSPITAL_TENANT, true, 50);
        boolean foundExportAudit = audits.stream()
                .anyMatch(a -> "FHIR_BUNDLE_EXPORT".equals(a.action()) && PATIENT_ID.toString().equals(a.resourceId()));
        assertTrue(foundExportAudit, "L'exportation du bundle de dossier complet doit être enregistrée au journal d'audit.");
    }
}
