package com.medscan.interop.fhir;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;
import com.medscan.clinical.Prescription;
import com.medscan.clinical.VitalSigns;
import com.medscan.security.jwt.JsonHelper;

/**
 * Service central du Hub d'Interopérabilité International HL7 FHIR R4 (MOD-07).
 * Expose les ressources Patient, Observation (LOINC), MedicationRequest et Bundle
 * avec respect scrupuleux du contrôle d'accès RBAC et traçabilité d'audit DPO immuable.
 */
public class FhirService {

    private final ClinicalService clinicalService;

    public FhirService(ClinicalService clinicalService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
    }

    public FhirService() {
        this(new ClinicalService());
    }

    public ClinicalService getClinicalService() {
        return clinicalService;
    }

    // ==========================================
    // CAPABILITY STATEMENT (METADATA)
    // ==========================================

    public String getCapabilityStatement(String baseUrl) {
        return FhirCapabilityStatement.generate(baseUrl);
    }

    // ==========================================
    // RESSOURCE FHIR R4 : PATIENT
    // ==========================================

    public String getPatient(UUID patientId, UUID actorUserId, String actorUsername,
                             String actorRole, UUID actorTenantId, String baseUrl) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        if (!clinicalService.canAccessPatient(patientId, actorUserId, actorTenantId, roles)) {
            throw new SecurityException("Accès non autorisé au patient FHIR: " + patientId);
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient FHIR introuvable: " + patientId));

        clinicalService.logAudit(actorUserId, actorUsername, actorRole, actorTenantId,
                "FHIR_PATIENT_READ", "Patient", patientId.toString(), "SUCCESS",
                "Consultation de la ressource FHIR R4 Patient pour " + patient.fullName());

        return FhirPatientMapper.toFhirJson(patient, baseUrl);
    }

    public String searchPatients(String nationalId, String name, UUID actorUserId, String actorUsername,
                                 String actorRole, UUID actorTenantId, String requestUrl) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        List<Patient> patients = clinicalService.searchPatients(actorTenantId, name != null ? name : nationalId, roles);

        if (nationalId != null && !nationalId.isBlank()) {
            patients = patients.stream()
                    .filter(p -> nationalId.equalsIgnoreCase(p.nationalId()))
                    .collect(Collectors.toList());
        }

        List<String> fhirPatients = patients.stream()
                .map(p -> FhirPatientMapper.toFhirJson(p, requestUrl))
                .collect(Collectors.toList());

        clinicalService.logAudit(actorUserId, actorUsername, actorRole, actorTenantId,
                "FHIR_PATIENT_SEARCH", "Bundle", "PATIENT_SEARCH", "SUCCESS",
                "Recherche FHIR Patient (" + fhirPatients.size() + " résultats trouvés)");

        return FhirBundleMapper.toSearchSetBundle(fhirPatients, requestUrl, fhirPatients.size());
    }

    public String createPatient(String fhirJson, UUID actorUserId, String actorUsername,
                                String actorRole, UUID actorTenantId, String baseUrl) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        if (!roles.contains("DOCTOR") && !roles.contains("NURSE") && !roles.contains("ADMIN")
                && !roles.contains("SUPER_ADMIN") && !roles.contains("TENANT_ADMIN")) {
            throw new SecurityException("Rôle insuffisant pour créer un patient via le Hub FHIR.");
        }

        Patient newPatient = FhirPatientMapper.parseFhirPatient(fhirJson, actorTenantId);
        Patient created = clinicalService.createPatient(newPatient, actorUserId, actorUsername, actorRole, actorTenantId);

        clinicalService.logAudit(actorUserId, actorUsername, actorRole, actorTenantId,
                "FHIR_PATIENT_CREATE", "Patient", created.id().toString(), "SUCCESS",
                "Création du dossier patient via ressource HL7 FHIR R4 Patient (" + created.fullName() + ")");

        return FhirPatientMapper.toFhirJson(created, baseUrl);
    }

    // ==========================================
    // RESSOURCE FHIR R4 : OBSERVATION (LOINC)
    // ==========================================

    public String searchObservations(UUID patientId, String loincCode, UUID actorUserId, String actorUsername,
                                     String actorRole, UUID actorTenantId, String requestUrl) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        if (!clinicalService.canAccessPatient(patientId, actorUserId, actorTenantId, roles)) {
            throw new SecurityException("Accès non autorisé aux observations du patient: " + patientId);
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable: " + patientId));

        List<VitalSigns> vitalsList = clinicalService.getVitalSigns(patientId);
        List<String> matchingObservations = new ArrayList<>();

        for (VitalSigns v : vitalsList) {
            List<String> obsList = FhirObservationMapper.toAllObservations(v, patient.fullName());
            for (String obs : obsList) {
                if (loincCode == null || loincCode.isBlank() || obs.contains(loincCode)) {
                    matchingObservations.add(obs);
                }
            }
        }

        clinicalService.logAudit(actorUserId, actorUsername, actorRole, actorTenantId,
                "FHIR_OBSERVATION_SEARCH", "Observation", patientId.toString(), "SUCCESS",
                "Recherche d'observations FHIR R4 / LOINC (" + matchingObservations.size() + " mesures trouvées)");

        return FhirBundleMapper.toSearchSetBundle(matchingObservations, requestUrl, matchingObservations.size());
    }

    // ==========================================
    // RESSOURCE FHIR R4 : MEDICATION REQUEST
    // ==========================================

    public String getMedicationRequest(String idOrCode, UUID actorUserId, String actorUsername,
                                       String actorRole, UUID actorTenantId, String baseUrl) {
        Optional<Prescription> rxOpt = clinicalService.findPrescriptionByCode(idOrCode);
        if (rxOpt.isEmpty()) {
            try {
                UUID id = UUID.fromString(idOrCode);
                rxOpt = clinicalService.getAllPrescriptionsMap().values().stream()
                        .flatMap(List::stream)
                        .filter(rx -> rx.id().equals(id))
                        .findFirst();
            } catch (IllegalArgumentException ignored) {}
        }

        Prescription rx = rxOpt.orElseThrow(() -> new IllegalArgumentException("Ordonnance FHIR MedicationRequest introuvable: " + idOrCode));

        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        if (!clinicalService.canAccessPatient(rx.patientId(), actorUserId, actorTenantId, roles)) {
            throw new SecurityException("Accès non autorisé à l'ordonnance FHIR: " + idOrCode);
        }

        clinicalService.logAudit(actorUserId, actorUsername, actorRole, actorTenantId,
                "FHIR_MEDICATION_REQUEST_READ", "MedicationRequest", rx.prescriptionCode(), "SUCCESS",
                "Consultation de l'ordonnance FHIR R4 MedicationRequest " + rx.prescriptionCode());

        return FhirMedicationRequestMapper.toFhirJson(rx, baseUrl);
    }

    public String searchMedicationRequests(UUID patientId, UUID actorUserId, String actorUsername,
                                           String actorRole, UUID actorTenantId, String requestUrl) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        if (!clinicalService.canAccessPatient(patientId, actorUserId, actorTenantId, roles)) {
            throw new SecurityException("Accès non autorisé aux ordonnances du patient: " + patientId);
        }

        List<Prescription> prescriptions = clinicalService.getPrescriptionsForPatient(patientId);
        List<String> fhirRx = prescriptions.stream()
                .map(rx -> FhirMedicationRequestMapper.toFhirJson(rx, requestUrl))
                .collect(Collectors.toList());

        clinicalService.logAudit(actorUserId, actorUsername, actorRole, actorTenantId,
                "FHIR_MEDICATION_REQUEST_SEARCH", "MedicationRequest", patientId.toString(), "SUCCESS",
                "Recherche des prescriptions FHIR R4 (" + fhirRx.size() + " ordonnances trouvées)");

        return FhirBundleMapper.toSearchSetBundle(fhirRx, requestUrl, fhirRx.size());
    }

    // ==========================================
    // EXPORT COMPLET DU DOSSIER EN FHIR BUNDLE
    // ==========================================

    /**
     * Exporte l'intégralité du Dossier Médical Partagé d'un patient sous forme d'un Bundle FHIR R4 standard
     * contenant Patient, toutes les Observations (constantes LOINC) et toutes les MedicationRequests.
     */
    public String exportPatientRecordAsBundle(UUID patientId, UUID actorUserId, String actorUsername,
                                             String actorRole, UUID actorTenantId, String bundleUrl) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        if (!clinicalService.canAccessPatient(patientId, actorUserId, actorTenantId, roles)) {
            throw new SecurityException("Accès non autorisé à l'export FHIR du patient: " + patientId);
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable: " + patientId));

        List<String> allEntries = new ArrayList<>();

        // 1. Ressource Patient
        allEntries.add(FhirPatientMapper.toFhirJson(patient, bundleUrl));

        // 2. Toutes les Observations vitales LOINC
        List<VitalSigns> vitals = clinicalService.getVitalSigns(patientId);
        for (VitalSigns v : vitals) {
            allEntries.addAll(FhirObservationMapper.toAllObservations(v, patient.fullName()));
        }

        // 3. Toutes les Ordonnances MedicationRequest
        List<Prescription> rxList = clinicalService.getPrescriptionsForPatient(patientId);
        for (Prescription rx : rxList) {
            allEntries.add(FhirMedicationRequestMapper.toFhirJson(rx, bundleUrl));
        }

        clinicalService.logAudit(actorUserId, actorUsername, actorRole, actorTenantId,
                "FHIR_BUNDLE_EXPORT", "Bundle", patientId.toString(), "SUCCESS",
                "Exportation complète du dossier médical en Bundle HL7 FHIR R4 (" + allEntries.size() + " ressources)");

        return FhirBundleMapper.toCollectionBundle(allEntries, bundleUrl);
    }
}
