package com.medscan.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalJsonMapper;
import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Consultation;
import com.medscan.clinical.Patient;
import com.medscan.clinical.Prescription;
import com.medscan.clinical.VitalSigns;
import com.medscan.security.jwt.JsonHelper;
import com.medscan.security.tenant.TenantRegistry;

/**
 * Service central du Module de Synchronisation Hors-Ligne & Delta-Sync (MOD-10).
 * Gère le téléchargement du coffre médical hors-ligne (Offline Vault),
 * l'ingestion idempotente des lots de modifications de terrain (Batch Push),
 * et la génération des fiches médicales d'urgence autonomes (Emergency Health Card).
 */
public class SyncService {

    public record SyncOperationRecord(
            UUID operationId,
            UUID entityId,
            String entityType,
            String action,
            Instant processedAt,
            String status,
            String details
    ) {}

    private final ClinicalService clinicalService;
    private final Map<UUID, SyncOperationRecord> idempotencyStore = new ConcurrentHashMap<>();

    public SyncService(ClinicalService clinicalService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
    }

    public SyncService() {
        this(new ClinicalService());
    }

    public ClinicalService getClinicalService() {
        return clinicalService;
    }

    public Map<UUID, SyncOperationRecord> getIdempotencyStore() {
        return Collections.unmodifiableMap(idempotencyStore);
    }

    // ==========================================
    // TÉLÉCHARGEMENT DU COFFRE HORS-LIGNE (PULL & DELTA)
    // ==========================================

    /**
     * Génère un paquet de données cliniques (SyncPackage) pour mise en cache hors-ligne
     * ou synchronisation différentielle (delta) depuis un curseur temporel (sinceTimestamp).
     */
    public SyncPackage generateOfflineVault(UUID actorUserId, String actorUsername, String actorRole,
                                           UUID actorTenantId, UUID patientIdFilter, Instant sinceTimestamp) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        List<Patient> candidatePatients = new ArrayList<>();

        if ("PATIENT".equalsIgnoreCase(actorRole)) {
            // Un patient ne peut accéder qu'à son propre dossier
            Optional<Patient> ownOpt = clinicalService.findPatientByLinkedUserId(actorUserId);
            if (ownOpt.isPresent()) {
                Patient own = ownOpt.get();
                if (patientIdFilter != null && !patientIdFilter.equals(own.id())) {
                    throw new SecurityException("Accès non autorisé: un patient ne peut synchroniser que son propre dossier.");
                }
                candidatePatients.add(own);
            } else if (patientIdFilter != null) {
                Patient candidate = clinicalService.findPatientById(patientIdFilter).orElse(null);
                if (candidate != null && (actorUserId.equals(candidate.linkedUserId()) || actorUserId.equals(candidate.id()))) {
                    candidatePatients.add(candidate);
                } else {
                    throw new SecurityException("Accès non autorisé: aucun dossier patient associé à cet utilisateur.");
                }
            } else {
                throw new SecurityException("Dossier patient introuvable pour cet utilisateur.");
            }
        } else if (patientIdFilter != null) {
            // Praticien ou admin consultant un patient spécifique
            if (!clinicalService.canAccessPatient(patientIdFilter, actorUserId, actorTenantId, roles)) {
                throw new SecurityException("Accès non autorisé au patient: " + patientIdFilter);
            }
            clinicalService.findPatientById(patientIdFilter).ifPresent(candidatePatients::add);
        } else {
            // Praticien ou administrateur synchronisant les patients de son établissement
            candidatePatients = clinicalService.searchPatients(actorTenantId, null, roles);
        }

        List<Patient> synchedPatients = new ArrayList<>();
        List<VitalSigns> synchedVitals = new ArrayList<>();
        List<Consultation> synchedConsultations = new ArrayList<>();
        List<Prescription> synchedPrescriptions = new ArrayList<>();
        List<EmergencyCard> synchedEmergencyCards = new ArrayList<>();

        for (Patient p : candidatePatients) {
            // Filtrage différentiel (Delta-Sync)
            boolean includePatient = (sinceTimestamp == null)
                    || (p.updatedAt() != null && !p.updatedAt().isBefore(sinceTimestamp))
                    || (p.createdAt() != null && !p.createdAt().isBefore(sinceTimestamp));

            if (includePatient) {
                synchedPatients.add(p);
            }

            // Constantes vitales du patient
            List<VitalSigns> vitals = clinicalService.getVitalSigns(p.id());
            for (VitalSigns v : vitals) {
                if (sinceTimestamp == null || (v.recordedAt() != null && !v.recordedAt().isBefore(sinceTimestamp))) {
                    synchedVitals.add(v);
                }
            }

            // Consultations du patient
            List<Consultation> consultations = clinicalService.getConsultations(p.id());
            for (Consultation c : consultations) {
                if (sinceTimestamp == null || (c.date() != null && !c.date().isBefore(sinceTimestamp))) {
                    synchedConsultations.add(c);
                }
            }

            // Ordonnances du patient
            List<Prescription> prescriptions = clinicalService.getPrescriptionsForPatient(p.id());
            for (Prescription rx : prescriptions) {
                boolean includeRx = (sinceTimestamp == null)
                        || (rx.dispensedAt() != null && !rx.dispensedAt().isBefore(sinceTimestamp))
                        || (rx.issuedAt() != null && !rx.issuedAt().isBefore(sinceTimestamp));
                if (includeRx) {
                    synchedPrescriptions.add(rx);
                }
            }

            // Fiche d'urgence compacte toujours générée
            synchedEmergencyCards.add(buildEmergencyCard(p));
        }

        int totalRecords = synchedPatients.size() + synchedVitals.size()
                + synchedConsultations.size() + synchedPrescriptions.size();

        clinicalService.logAudit(
                actorUserId,
                actorUsername,
                actorRole,
                actorTenantId,
                "OFFLINE_VAULT_PULLED",
                "SyncPackage",
                (patientIdFilter != null ? patientIdFilter.toString() : "ORGANIZATION"),
                "SUCCESS",
                "Téléchargement du coffre hors-ligne (" + totalRecords + " enregistrements)"
                        + (sinceTimestamp != null ? " [Différentiel depuis: " + sinceTimestamp + "]" : " [Complet]")
        );

        return new SyncPackage(
                Instant.now(),
                sinceTimestamp,
                totalRecords,
                synchedPatients,
                synchedVitals,
                synchedConsultations,
                synchedPrescriptions,
                synchedEmergencyCards
        );
    }

    // ==========================================
    // FICHE MÉDICALE D'URGENCE (EMERGENCY CARD)
    // ==========================================

    /**
     * Génère la fiche médicale d'urgence autonome d'un patient pour affichage QR-code ou triage.
     */
    public EmergencyCard generateEmergencyCard(UUID patientId, UUID actorUserId, String actorUsername,
                                               String actorRole, UUID actorTenantId) {
        Set<String> roles = (actorRole != null) ? Set.of(actorRole) : Collections.emptySet();
        if (!clinicalService.canAccessPatient(patientId, actorUserId, actorTenantId, roles)) {
            throw new SecurityException("Accès non autorisé à la fiche d'urgence du patient: " + patientId);
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable avec l'identifiant: " + patientId));

        EmergencyCard card = buildEmergencyCard(patient);

        clinicalService.logAudit(
                actorUserId,
                actorUsername,
                actorRole,
                actorTenantId,
                "EMERGENCY_CARD_ACCESSED",
                "EmergencyCard",
                patientId.toString(),
                "SUCCESS",
                "Consultation de la fiche d'urgence autonome pour " + patient.fullName() + " (Groupe: " + card.bloodGroup() + ")"
        );

        return card;
    }

    public EmergencyCard buildEmergencyCard(Patient p) {
        List<VitalSigns> vitals = clinicalService.getVitalSigns(p.id());
        VitalSigns latestVitals = vitals.isEmpty() ? null : vitals.get(0);

        List<Prescription> rxList = clinicalService.getPrescriptionsForPatient(p.id());
        int activeRx = (int) rxList.stream().filter(pr -> "ISSUED".equalsIgnoreCase(pr.status())).count();

        Double w = latestVitals != null && latestVitals.weightKg() != null ? latestVitals.weightKg() : p.weightKg();
        Double h = latestVitals != null && latestVitals.heightCm() != null ? latestVitals.heightCm() : p.heightCm();
        Double bmi = latestVitals != null && latestVitals.bmi() != null ? latestVitals.bmi() : p.bmi();
        String bmiCat = latestVitals != null && latestVitals.bmiCategory() != null ? latestVitals.bmiCategory() : p.bmiCategory();

        String bp = (latestVitals != null && latestVitals.systolicBp() != null && latestVitals.diastolicBp() != null)
                ? (latestVitals.systolicBp() + "/" + latestVitals.diastolicBp() + " mmHg")
                : "N/A";
        Integer hr = latestVitals != null ? latestVitals.heartRate() : null;
        Double spo2 = latestVitals != null ? latestVitals.oxygenSaturation() : null;
        String triage = (latestVitals != null && latestVitals.triageLevel() != null) ? latestVitals.triageLevel() : "NORMAL";
        Instant lastRecorded = (latestVitals != null && latestVitals.recordedAt() != null) ? latestVitals.recordedAt() : p.updatedAt();

        return new EmergencyCard(
                p.id(),
                p.nationalId(),
                p.fullName(),
                p.gender(),
                p.birthDate(),
                p.age(),
                p.bloodGroup(),
                p.allergies(),
                p.chronicConditions(),
                p.emergencyContact(),
                w,
                h,
                bmi,
                bmiCat,
                bp,
                hr,
                spo2,
                triage,
                lastRecorded,
                activeRx,
                Instant.now(),
                null // Calcul automatique de l'empreinte SHA-256 dans le constructeur
        );
    }

    // ==========================================
    // INGESTION IDEMPOTENTE DES LOTS (PUSH BATCH)
    // ==========================================

    /**
     * Traite un lot d'opérations hors-ligne soumises depuis un terminal mobile.
     * Chaque opération est validée et exécutée de manière strictement idempotente.
     */
    public SyncBatchResponse processBatchPush(SyncBatchRequest request, UUID actorUserId, String actorUsername,
                                             String actorRole, UUID actorTenantId) {
        List<SyncOperationResult> results = new ArrayList<>();
        int appliedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;

        for (SyncOperation op : request.operations()) {
            // 1. Détection de doublon (Idempotence)
            SyncOperationRecord existing = idempotencyStore.get(op.operationId());
            if (existing != null) {
                duplicateCount++;
                results.add(SyncOperationResult.duplicate(
                        op.operationId(),
                        existing.entityType(),
                        existing.entityId(),
                        "Opération déjà appliquée le " + existing.processedAt() + " (déduplication idempotente)"
                ));
                continue;
            }

            // 2. Traitement selon l'entité clinique
            try {
                String type = op.entityType().toUpperCase();
                String action = op.action().toUpperCase();
                String payload = op.payloadJson();

                if ("VITAL_SIGNS".equals(type)) {
                    String pIdStr = JsonHelper.getString(payload, "patientId");
                    if (pIdStr == null || pIdStr.isBlank()) {
                        throw new IllegalArgumentException("L'identifiant du patient (patientId) est requis pour les constantes.");
                    }
                    UUID patientId = UUID.fromString(pIdStr);

                    VitalSigns parsed = ClinicalJsonMapper.parseVitalSigns(payload, patientId, actorUsername, actorRole);
                    // Préserver l'horodatage exact mesuré sur le terrain
                    Instant recordTime = op.occurredAt() != null ? op.occurredAt() : Instant.now();
                    VitalSigns vitalsWithFieldTime = new VitalSigns(
                            parsed.id(),
                            parsed.patientId(),
                            recordTime,
                            parsed.systolicBp(),
                            parsed.diastolicBp(),
                            parsed.heartRate(),
                            parsed.temperature(),
                            parsed.weightKg(),
                            parsed.heightCm(),
                            parsed.bmi(),
                            parsed.bmiCategory(),
                            parsed.bloodGlucose(),
                            parsed.oxygenSaturation(),
                            parsed.respiratoryRate(),
                            parsed.painScale(),
                            parsed.bloodGroup(),
                            parsed.allergies(),
                            parsed.chronicConditions(),
                            parsed.emergencyContact(),
                            parsed.triageLevel(),
                            parsed.notes(),
                            parsed.customFields(),
                            parsed.recordedBy(),
                            parsed.recordedByRole()
                    );

                    VitalSigns saved = clinicalService.recordVitals(vitalsWithFieldTime, actorUserId, actorUsername, actorRole, actorTenantId);
                    idempotencyStore.put(op.operationId(), new SyncOperationRecord(
                            op.operationId(),
                            saved.id(),
                            "VITAL_SIGNS",
                            action,
                            Instant.now(),
                            "APPLIED",
                            "Constantes enregistrées"
                    ));
                    appliedCount++;
                    results.add(SyncOperationResult.applied(op.operationId(), "VITAL_SIGNS", saved.id(), "Constantes vitales synchronisées avec succès."));

                } else if ("CONSULTATION".equals(type)) {
                    String pIdStr = JsonHelper.getString(payload, "patientId");
                    if (pIdStr == null || pIdStr.isBlank()) {
                        throw new IllegalArgumentException("L'identifiant du patient (patientId) est requis pour la consultation.");
                    }
                    UUID patientId = UUID.fromString(pIdStr);

                    Consultation parsed = ClinicalJsonMapper.parseConsultation(payload, patientId, actorUserId, actorUsername, actorTenantId);
                    Instant consultTime = op.occurredAt() != null ? op.occurredAt() : Instant.now();
                    Consultation consultWithFieldTime = new Consultation(
                            parsed.id(),
                            parsed.patientId(),
                            parsed.doctorId(),
                            parsed.doctorName(),
                            parsed.tenantId(),
                            consultTime,
                            parsed.chiefComplaint(),
                            parsed.examinationNotes(),
                            parsed.diagnosis(),
                            parsed.treatmentPlan(),
                            parsed.weightKg(),
                            parsed.heightCm(),
                            parsed.bmi(),
                            parsed.bmiCategory(),
                            parsed.systolicBp(),
                            parsed.diastolicBp(),
                            parsed.heartRate(),
                            parsed.temperature(),
                            parsed.oxygenSaturation(),
                            parsed.customFields()
                    );

                    Consultation saved = clinicalService.recordConsultation(consultWithFieldTime, actorUserId, actorUsername, actorRole, actorTenantId);
                    idempotencyStore.put(op.operationId(), new SyncOperationRecord(
                            op.operationId(),
                            saved.id(),
                            "CONSULTATION",
                            action,
                            Instant.now(),
                            "APPLIED",
                            "Consultation enregistrée"
                    ));
                    appliedCount++;
                    results.add(SyncOperationResult.applied(op.operationId(), "CONSULTATION", saved.id(), "Consultation médicale synchronisée avec succès."));

                } else if ("PATIENT".equals(type)) {
                    if ("UPDATE".equals(action)) {
                        String pIdStr = JsonHelper.getString(payload, "patientId");
                        if (pIdStr == null || pIdStr.isBlank()) {
                            pIdStr = JsonHelper.getString(payload, "id");
                        }
                        if (pIdStr == null || pIdStr.isBlank()) {
                            throw new IllegalArgumentException("L'identifiant du patient est requis pour la mise à jour.");
                        }
                        UUID patientId = UUID.fromString(pIdStr);
                        Patient existingPatient = clinicalService.findPatientById(patientId)
                                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable avec l'ID: " + patientId));

                        Patient updated = ClinicalJsonMapper.parsePatientUpdate(payload, existingPatient);
                        clinicalService.updatePatient(patientId, updated, actorUserId, actorUsername, actorRole, actorTenantId);

                        idempotencyStore.put(op.operationId(), new SyncOperationRecord(
                                op.operationId(),
                                patientId,
                                "PATIENT",
                                action,
                                Instant.now(),
                                "APPLIED",
                                "Mise à jour dossier patient"
                        ));
                        appliedCount++;
                        results.add(SyncOperationResult.applied(op.operationId(), "PATIENT", patientId, "Dossier patient mis à jour avec succès."));
                    } else {
                        // CREATE
                        Patient newPatient = ClinicalJsonMapper.parsePatient(payload, actorTenantId);
                        Patient created = clinicalService.createPatient(newPatient, actorUserId, actorUsername, actorRole, actorTenantId);

                        idempotencyStore.put(op.operationId(), new SyncOperationRecord(
                                op.operationId(),
                                created.id(),
                                "PATIENT",
                                action,
                                Instant.now(),
                                "APPLIED",
                                "Création dossier patient"
                        ));
                        appliedCount++;
                        results.add(SyncOperationResult.applied(op.operationId(), "PATIENT", created.id(), "Dossier patient créé avec succès."));
                    }
                } else {
                    throw new UnsupportedOperationException("Type d'entité non pris en charge pour la synchronisation: " + type);
                }

            } catch (Exception ex) {
                errorCount++;
                results.add(SyncOperationResult.rejected(op.operationId(), op.entityType(), "Échec de synchronisation: " + ex.getMessage()));
            }
        }

        // 3. Delta de réconciliation bidirectionnelle si le client a fourni un curseur pullSince
        SyncPackage delta = null;
        if (request.pullSince() != null) {
            delta = generateOfflineVault(actorUserId, actorUsername, actorRole, actorTenantId, null, request.pullSince());
        }

        // 4. Traçabilité d'audit
        clinicalService.logAudit(
                actorUserId,
                actorUsername,
                actorRole,
                actorTenantId,
                "SYNC_BATCH_PUSHED",
                "SyncBatch",
                request.batchId().toString(),
                errorCount > 0 ? "PARTIAL" : "SUCCESS",
                "Lot synchronisé depuis le terminal " + request.deviceId()
                        + " (" + appliedCount + " appliquées, " + duplicateCount + " doublons ignorés, " + errorCount + " erreurs)"
        );

        return new SyncBatchResponse(
                request.batchId(),
                Instant.now(),
                request.operations().size(),
                appliedCount,
                duplicateCount,
                errorCount,
                results,
                delta
        );
    }
}
