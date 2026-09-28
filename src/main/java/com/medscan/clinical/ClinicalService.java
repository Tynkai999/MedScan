package com.medscan.clinical;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Service central du Dossier Médical Partagé (MOD-03), des Prescriptions (MOD-08),
 * des Accès d'Urgence Break-Glass (MOD-04) et du Journal d'Audit (MOD-11).
 *
 * Applique l'isolation multi-tenant stricte et la traçabilité des accès aux données de santé.
 */
public class ClinicalService {

    private static final UUID PLATFORM_TENANT = UUID.fromString("b47c7913-35d0-43e3-8ec1-5614dec9ffcd");

    private final Map<UUID, Patient> patientsById = new ConcurrentHashMap<>();
    private final Map<UUID, List<VitalSigns>> vitalsByPatientId = new ConcurrentHashMap<>();
    private final Map<UUID, List<Consultation>> consultationsByPatientId = new ConcurrentHashMap<>();
    private final Map<UUID, List<Prescription>> prescriptionsByPatientId = new ConcurrentHashMap<>();
    private final Map<String, Prescription> prescriptionsByCode = new ConcurrentHashMap<>();
    private final List<BreakGlassRecord> breakGlassRecords = new CopyOnWriteArrayList<>();
    private final List<AuditEvent> auditLogs = new CopyOnWriteArrayList<>();

    public ClinicalService() {
        initSeedData();
    }

    // ==========================================
    // GESTION DES PATIENTS (MOD-03)
    // ==========================================

    public List<Patient> searchPatients(UUID tenantId, String query, Set<String> roles) {
        boolean canSeeAll = roles.contains("SUPER_ADMIN") || roles.contains("AUDITOR") || PLATFORM_TENANT.equals(tenantId);

        return patientsById.values().stream()
                .filter(p -> canSeeAll || p.tenantId().equals(tenantId))
                .filter(p -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.toLowerCase();
                    return p.fullName().toLowerCase().contains(q)
                            || p.nationalId().toLowerCase().contains(q)
                            || p.phone().contains(q);
                })
                .sorted((a, b) -> a.lastName().compareToIgnoreCase(b.lastName()))
                .collect(Collectors.toList());
    }

    public Optional<Patient> findPatientById(UUID patientId) {
        return Optional.ofNullable(patientsById.get(patientId));
    }

    public Optional<Patient> findPatientByLinkedUserId(UUID userId) {
        return patientsById.values().stream()
                .filter(p -> userId.equals(p.linkedUserId()))
                .findFirst();
    }

    public boolean canAccessPatient(UUID patientId, UUID requesterUserId, UUID requesterTenantId, Set<String> roles) {
        Patient patient = patientsById.get(patientId);
        if (patient == null) return false;

        // 1. Le patient lui-même a accès à son propre dossier
        if (requesterUserId.equals(patient.linkedUserId())) {
            return true;
        }

        // 2. Super Admin et Auditeur de la plateforme
        if (roles.contains("SUPER_ADMIN") || roles.contains("AUDITOR") || PLATFORM_TENANT.equals(requesterTenantId)) {
            return true;
        }

        // 3. Personnel soignant ou administratif du MÊME tenant (Hôpital / Clinique)
        if (patient.tenantId().equals(requesterTenantId)) {
            return true;
        }

        // 4. Accès d'urgence dérogatoire (Break-Glass) actif pour ce praticien
        return hasActiveBreakGlass(patientId, requesterUserId);
    }

    public Patient createPatient(Patient patient, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        patientsById.put(patient.id(), patient);
        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "PATIENT_CREATED", "Patient", patient.id().toString(), "SUCCESS",
                "Création du dossier patient pour: " + patient.fullName() + " (" + patient.nationalId() + ")" +
                (patient.bmi() != null ? " [IMC: " + patient.bmi() + " - " + patient.bmiCategory() + "]" : ""));
        return patient;
    }

    public Patient updatePatient(UUID patientId, Patient updates, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        Patient existing = patientsById.get(patientId);
        if (existing == null) {
            throw new IllegalArgumentException("Patient introuvable avec l'ID: " + patientId);
        }
        patientsById.put(patientId, updates);
        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "PATIENT_UPDATED", "Patient", patientId.toString(), "SUCCESS",
                "Mise à jour du dossier patient pour: " + updates.fullName() + " (" + updates.nationalId() + ")" +
                (updates.bmi() != null ? " [IMC: " + updates.bmi() + " - " + updates.bmiCategory() + "]" : ""));
        return updates;
    }

    // ==========================================
    // CONSTANTES VITALES (MOD-03)
    // ==========================================

    public VitalSigns recordVitals(VitalSigns vitals, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        Patient patient = patientsById.get(vitals.patientId());
        VitalSigns enriched = (patient != null) ? vitals.withPatientContext(patient) : vitals;
        vitalsByPatientId.computeIfAbsent(enriched.patientId(), k -> new CopyOnWriteArrayList<>()).add(0, enriched);

        if (patient != null) {
            Patient updatedPatient = patient.mergeUpdates(
                    null, null, null, null, null,
                    enriched.bloodGroup(),
                    null,
                    enriched.emergencyContact(),
                    enriched.allergies(),
                    enriched.chronicConditions(),
                    enriched.weightKg(),
                    enriched.heightCm(),
                    enriched.customFields()
            );
            patientsById.put(patient.id(), updatedPatient);
        }

        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "VITALS_RECORDED", "VitalSigns", enriched.id().toString(), "SUCCESS",
                "Constantes enregistrées pour patient " + enriched.patientId() + " (Tension: " + enriched.systolicBp() + "/" + enriched.diastolicBp() + " mmHg, Pouls: " + enriched.heartRate() + " bpm, SpO2: " + enriched.oxygenSaturation() + "%, Groupe: " + enriched.bloodGroup() + (enriched.bmi() != null ? ", IMC: " + enriched.bmi() : "") + ")");
        return enriched;
    }

    public List<VitalSigns> getVitalSigns(UUID patientId) {
        return vitalsByPatientId.getOrDefault(patientId, Collections.emptyList());
    }

    public Map<UUID, Patient> getAllPatientsMap() {
        return Collections.unmodifiableMap(patientsById);
    }

    public Map<UUID, List<VitalSigns>> getAllVitalsMap() {
        return Collections.unmodifiableMap(vitalsByPatientId);
    }

    public Map<UUID, List<Consultation>> getAllConsultationsMap() {
        return Collections.unmodifiableMap(consultationsByPatientId);
    }

    public Map<UUID, List<Prescription>> getAllPrescriptionsMap() {
        return Collections.unmodifiableMap(prescriptionsByPatientId);
    }

    public Map<String, Prescription> getAllPrescriptionsByCodeMap() {
        return Collections.unmodifiableMap(prescriptionsByCode);
    }

    public List<BreakGlassRecord> getAllBreakGlassRecords() {
        return Collections.unmodifiableList(breakGlassRecords);
    }

    // ==========================================
    // CONSULTATIONS & NOTES CLINIQUES (MOD-03)
    // ==========================================

    public Consultation recordConsultation(Consultation consultation, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        Patient patient = patientsById.get(consultation.patientId());
        Consultation toRecord = consultation;

        if (patient != null) {
            Double w = consultation.weightKg() != null ? consultation.weightKg() : patient.weightKg();
            Double h = consultation.heightCm() != null ? consultation.heightCm() : patient.heightCm();
            Double bmi = consultation.bmi();
            String bmiCat = consultation.bmiCategory();
            if (bmi == null && w != null && h != null && h > 0 && w > 0) {
                bmi = Patient.calculateBmi(w, h);
                bmiCat = Patient.classifyBmi(bmi);
            }

            if (toRecord.bmi() == null && bmi != null) {
                toRecord = new Consultation(
                        consultation.id(),
                        consultation.patientId(),
                        consultation.doctorId(),
                        consultation.doctorName(),
                        consultation.tenantId(),
                        consultation.date(),
                        consultation.chiefComplaint(),
                        consultation.examinationNotes(),
                        consultation.diagnosis(),
                        consultation.treatmentPlan(),
                        w,
                        h,
                        bmi,
                        bmiCat,
                        consultation.systolicBp(),
                        consultation.diastolicBp(),
                        consultation.heartRate(),
                        consultation.temperature(),
                        consultation.oxygenSaturation(),
                        consultation.customFields()
                );
            }

            if (consultation.weightKg() != null || consultation.heightCm() != null || (consultation.customFields() != null && !consultation.customFields().isEmpty())) {
                Patient updatedPatient = patient.mergeUpdates(
                        null, null, null, null, null, null, null, null,
                        null, null,
                        consultation.weightKg(),
                        consultation.heightCm(),
                        consultation.customFields()
                );
                patientsById.put(patient.id(), updatedPatient);
            }
        }

        consultationsByPatientId.computeIfAbsent(toRecord.patientId(), k -> new CopyOnWriteArrayList<>()).add(0, toRecord);
        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "CONSULTATION_CREATED", "Consultation", toRecord.id().toString(), "SUCCESS",
                "Consultation enregistrée par " + toRecord.doctorName() + " : " + toRecord.diagnosis() +
                (toRecord.bmi() != null ? " (IMC: " + toRecord.bmi() + " - " + toRecord.bmiCategory() + ")" : ""));
        return toRecord;
    }

    public List<Consultation> getConsultations(UUID patientId) {
        return consultationsByPatientId.getOrDefault(patientId, Collections.emptyList());
    }

    // ==========================================
    // ORDONNANCES & PRESCRIPTIONS (MOD-08)
    // ==========================================

    public Prescription issuePrescription(Prescription prescription, UUID doctorId, String doctorName, UUID tenantId) {
        prescriptionsByPatientId.computeIfAbsent(prescription.patientId(), k -> new CopyOnWriteArrayList<>()).add(0, prescription);
        prescriptionsByCode.put(prescription.prescriptionCode().toUpperCase(), prescription);

        logAudit(doctorId, doctorName, "DOCTOR", tenantId,
                "PRESCRIPTION_ISSUED", "Prescription", prescription.prescriptionCode(), "SUCCESS",
                "Ordonnance émise " + prescription.prescriptionCode() + " avec " + prescription.items().size() + " lignes de traitement.");
        return prescription;
    }

    public List<Prescription> getPrescriptionsForPatient(UUID patientId) {
        return prescriptionsByPatientId.getOrDefault(patientId, Collections.emptyList());
    }

    public Optional<Prescription> findPrescriptionByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(prescriptionsByCode.get(code.trim().toUpperCase()));
    }

    public synchronized Prescription dispensePrescription(String code, String pharmacistName, UUID pharmacistTenantId, UUID actorId) {
        Prescription current = findPrescriptionByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Ordonnance introuvable avec le code: " + code));

        if ("DISPENSED".equalsIgnoreCase(current.status())) {
            throw new IllegalStateException("Cette ordonnance a déjà été dispensée le " + current.dispensedAt() + " par " + current.dispensedBy());
        }

        Prescription updated = current.withDispensation(pharmacistName, Instant.now());
        prescriptionsByCode.put(code.toUpperCase(), updated);

        // Mettre à jour dans la liste du patient
        List<Prescription> list = prescriptionsByPatientId.get(current.patientId());
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(current.id())) {
                    list.set(i, updated);
                    break;
                }
            }
        }

        logAudit(actorId, pharmacistName, "PHARMACIST", pharmacistTenantId,
                "PRESCRIPTION_DISPENSED", "Prescription", code, "SUCCESS",
                "Dispensation complète de l'ordonnance " + code + " effectuée avec succès.");

        return updated;
    }

    // ==========================================
    // ACCÈS D'URGENCE (BREAK-GLASS - MOD-04)
    // ==========================================

    public BreakGlassRecord triggerBreakGlass(UUID patientId, UUID doctorId, String doctorName, UUID doctorTenantId, String reason) {
        BreakGlassRecord record = new BreakGlassRecord(
                UUID.randomUUID(),
                patientId,
                doctorId,
                doctorName,
                doctorTenantId,
                reason,
                Instant.now(),
                "CRITICAL_EMERGENCY"
        );
        breakGlassRecords.add(record);

        logAudit(doctorId, doctorName, "DOCTOR", doctorTenantId,
                "BREAK_GLASS_TRIGGERED", "Patient", patientId.toString(), "CRITICAL",
                "[ALERTE DPO] Dérogation d'urgence vitale activée par Dr. " + doctorName + " - Motif: " + reason);

        return record;
    }

    public boolean hasActiveBreakGlass(UUID patientId, UUID doctorId) {
        return breakGlassRecords.stream()
                .anyMatch(bg -> bg.patientId().equals(patientId) && bg.doctorId().equals(doctorId));
    }

    // ==========================================
    // JOURNAL D'AUDIT & OBSERVABILITÉ (MOD-11)
    // ==========================================

    public void logAudit(UUID actorId, String actorUsername, String actorRole, UUID tenantId,
                         String action, String resource, String resourceId, String status, String details) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                actorId,
                actorUsername,
                actorRole,
                tenantId,
                action,
                resource,
                resourceId,
                status,
                details
        );
        auditLogs.add(0, event); // Ingestion tête de liste pour ordre chronologique inverse
    }

    public List<AuditEvent> getAuditLogs(UUID requesterTenantId, boolean isPlatformAuditor, int limit) {
        return auditLogs.stream()
                .filter(ev -> isPlatformAuditor || PLATFORM_TENANT.equals(requesterTenantId) || ev.tenantId().equals(requesterTenantId))
                .limit(limit > 0 ? limit : 50)
                .collect(Collectors.toList());
    }

    // ==========================================
    // DONNÉES DE DÉMONSTRATION (BURKINA FASO / AFRIQUE)
    // ==========================================

    private void initSeedData() {
        UUID tenantHospital = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
        UUID tenantHospital2 = UUID.fromString("f56de30f-c802-42c6-8587-707a8d1a9814");
        UUID doctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
        UUID patientUserId = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4");

        // 1. Patient Fatou Ouedraogo (liée à l'utilisateur patient@medscan.org)
        UUID p1Id = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
        Patient p1 = new Patient(
                p1Id,
                "BFA-2026-008412",
                "Fatou",
                "Ouedraogo",
                "1994-06-18",
                "F",
                "A+",
                "+226 70 12 34 56",
                "Moussa Ouedraogo (+226 76 11 22 33)",
                List.of("Pénicilline", "Arachide"),
                List.of("Asthme léger"),
                tenantHospital,
                patientUserId,
                Instant.now().minusSeconds(86400 * 30)
        );
        patientsById.put(p1Id, p1);

        // Constantes Fatou (enrichies)
        VitalSigns v1 = new VitalSigns(
                UUID.randomUUID(), p1Id, Instant.now().minusSeconds(86400 * 2),
                120, 80, 72, 36.8, 62.5, 168.0, 22.1, 0.95,
                98.5, 16, 0, "A+",
                List.of("Pénicilline", "Arachide"),
                List.of("Asthme léger"),
                "Moussa Ouedraogo (+226 76 11 22 33)",
                "NORMAL",
                "Constantes stables en consultation de routine.",
                "Inf. Awa Kaboré", "NURSE"
        );
        vitalsByPatientId.computeIfAbsent(p1Id, k -> new CopyOnWriteArrayList<>()).add(v1);

        // Consultation Fatou
        Consultation c1 = new Consultation(
                UUID.randomUUID(), p1Id, doctorId, "Dr. Seydou Traore", tenantHospital,
                Instant.now().minusSeconds(86400 * 2),
                "Bilan de santé trimestriel et suivi allergologique",
                "Patient stable. Murmure vésiculaire clair, pas de râles sibilants. Pression artérielle normale.",
                "Asthme intermittent contrôlé sous traitement de crise.",
                "Poursuivre Salbutamol en cas de gêne. Éviction stricte des dérivés pénicilliniques."
        );
        consultationsByPatientId.computeIfAbsent(p1Id, k -> new CopyOnWriteArrayList<>()).add(c1);

        // Prescription Fatou
        Prescription rx1 = new Prescription(
                UUID.randomUUID(),
                "RX-2026-0042",
                p1Id,
                "Fatou Ouedraogo",
                doctorId,
                "Dr. Seydou Traore",
                tenantHospital,
                Instant.now().minusSeconds(86400 * 2),
                "ISSUED",
                List.of(
                        new PrescriptionItem(UUID.randomUUID(), "Salbutamol 100 µg spray", "2 bouffées", "En cas de crise", 30, "Inhalation buccale"),
                        new PrescriptionItem(UUID.randomUUID(), "Paracétamol 1g comprimés", "1 comprimé", "Toutes les 8h si douleur", 5, "Ne pas dépasser 3g/jour")
                ),
                null,
                null
        );
        prescriptionsByPatientId.computeIfAbsent(p1Id, k -> new CopyOnWriteArrayList<>()).add(rx1);
        prescriptionsByCode.put("RX-2026-0042", rx1);

        // 2. Patient Ibrahim Compaore (Patient d'un autre tenant pour tester le cloisonnement et le Break-Glass)
        UUID p2Id = UUID.fromString("dababb25-8dc9-402c-b527-15d9a4b1f380");
        Patient p2 = new Patient(
                p2Id,
                "BFA-2026-009187",
                "Ibrahim",
                "Compaore",
                "1962-11-04",
                "M",
                "O+",
                "+226 78 45 67 89",
                "Salamata Compaore (+226 70 99 88 77)",
                List.of("Sulfamides"),
                List.of("Diabète de Type 2", "Hypertension Artérielle"),
                tenantHospital2, // Tenant différent !
                null,
                Instant.now().minusSeconds(86400 * 60)
        );
        patientsById.put(p2Id, p2);

        // Constantes Ibrahim (Surveillance HTA et Diabète)
        VitalSigns v2 = new VitalSigns(
                UUID.randomUUID(), p2Id, Instant.now().minusSeconds(86400 * 5),
                148, 94, 84, 37.1, 84.0, 178.0, 26.5, 1.45,
                96.0, 18, 1, "O+",
                List.of("Sulfamides"),
                List.of("Diabète de Type 2", "Hypertension Artérielle"),
                "Salamata Compaore (+226 70 99 88 77)",
                "ATTENTION",
                "Tension artérielle élevée et glycémie à jeun limite. Réajuster antihypertenseur.",
                "Dr. Seydou Traore", "DOCTOR"
        );
        vitalsByPatientId.computeIfAbsent(p2Id, k -> new CopyOnWriteArrayList<>()).add(v2);

        // Audit de démarrage
        logAudit(UUID.fromString("f70a1335-b31f-4193-88ef-ce3460f3d029"),
                "system-bootstrap", "SYSTEM", PLATFORM_TENANT,
                "PLATFORM_BOOTSTRAP", "System", "MedScan-v1", "SUCCESS",
                "Initialisation du registre clinique MedScan Enterprise avec données de référence.");
    }
}
