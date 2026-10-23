package com.medscan.referral;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.medscan.certificate.HospitalSeal;
import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;
import com.medscan.messaging.MessagingNotificationService;
import com.medscan.security.tenant.Tenant;
import com.medscan.security.tenant.TenantRegistry;

/**
 * Service de gestion des orientations et transferts médicaux inter-services et inter-établissements (MOD-16).
 * Assure la continuité des soins, l'envoi d'alertes instantanées et le scellage numérique des orientations.
 */
public class ReferralService {

    private final ClinicalService clinicalService;
    private final TenantRegistry tenantRegistry;
    private final MessagingNotificationService messagingService;
    private final Map<UUID, MedicalReferral> referralsById = new ConcurrentHashMap<>();
    private final Map<String, MedicalReferral> referralsByCode = new ConcurrentHashMap<>();
    private final AtomicInteger referralCounter = new AtomicInteger(100);

    public ReferralService(ClinicalService clinicalService, TenantRegistry tenantRegistry, MessagingNotificationService messagingService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
        this.tenantRegistry = (tenantRegistry != null) ? tenantRegistry : new TenantRegistry();
        this.messagingService = (messagingService != null) ? messagingService : new MessagingNotificationService(this.clinicalService);
        initSeedReferrals();
    }

    public ReferralService() {
        this(new ClinicalService(), new TenantRegistry(), new MessagingNotificationService());
    }

    public MedicalReferral createReferral(
            UUID patientId,
            UUID referringDoctorId,
            String referringDoctorName,
            String doctorLicenseNumber,
            UUID sourceTenantId,
            String sourceDepartment,
            UUID targetTenantId,
            String targetDepartment,
            UUID targetDoctorId,
            String targetDoctorName,
            String reason,
            String priority,
            String clinicalSummary
    ) {
        if (patientId == null) {
            throw new IllegalArgumentException("L'identifiant du patient est obligatoire.");
        }
        if (referringDoctorId == null) {
            throw new IllegalArgumentException("L'identifiant du médecin référent est obligatoire.");
        }
        if (sourceTenantId == null) {
            throw new IllegalArgumentException("L'établissement de santé d'origine (sourceTenantId) est requis.");
        }
        if (targetDepartment == null || targetDepartment.isBlank()) {
            throw new IllegalArgumentException("Le service ou la spécialité cible est obligatoire.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Le motif de l'orientation médicale est obligatoire.");
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable: " + patientId));

        UUID resolvedTargetTenantId = (targetTenantId != null) ? targetTenantId : sourceTenantId;

        Tenant sourceTenant = tenantRegistry.findById(sourceTenantId).orElse(null);
        String sourceTenantName = sourceTenant != null ? sourceTenant.name() : tenantRegistry.resolveTenantDisplayName(sourceTenantId);
        String sourceTenantCode = sourceTenant != null ? sourceTenant.code() : "HOSPITAL";
        String sourceAddress = sourceTenant != null ? sourceTenant.address() + ", " + sourceTenant.city() : "Burkina Faso";
        String sourcePhone = sourceTenant != null ? sourceTenant.phone() : "+226 25 00 00 00";
        String sourceEmail = sourceTenant != null ? sourceTenant.email() : "contact@medscan.org";

        Tenant targetTenant = tenantRegistry.findById(resolvedTargetTenantId).orElse(null);
        String targetTenantName = targetTenant != null ? targetTenant.name() : tenantRegistry.resolveTenantDisplayName(resolvedTargetTenantId);

        String code = "REF-2026-" + String.format("%04d", referralCounter.incrementAndGet());
        Instant now = Instant.now();

        // Calcul du cachet numérique officiel de l'hôpital pour la référence
        String hash = HospitalSeal.computeVerificationHash(sourceTenantId, code, patientId, now, referringDoctorName);
        HospitalSeal seal = new HospitalSeal(
                sourceTenantId,
                sourceTenantName,
                sourceTenantCode,
                sourceAddress,
                sourcePhone,
                sourceEmail,
                "MS-SANTE-REF-" + sourceTenantCode,
                now,
                referringDoctorName != null ? referringDoctorName : "Médecin Référent",
                doctorLicenseNumber != null ? doctorLicenseNumber : "OM-BF-REF",
                hash,
                "https://medscan-sluw.onrender.com/medscan/api/v1/certificates/verify?code=" + code + "&hash=" + hash
        );

        MedicalReferral referral = new MedicalReferral(
                UUID.randomUUID(),
                code,
                patientId,
                patient.fullName(),
                patient.phone(),
                sourceTenantId,
                sourceTenantName,
                sourceDepartment != null ? sourceDepartment.trim() : "Médecine Générale",
                referringDoctorId,
                referringDoctorName != null ? referringDoctorName.trim() : "Médecin Référent",
                resolvedTargetTenantId,
                targetTenantName,
                targetDepartment.trim(),
                targetDoctorId,
                targetDoctorName,
                reason.trim(),
                priority != null ? priority.trim().toUpperCase() : "ROUTINE",
                clinicalSummary != null ? clinicalSummary.trim() : "Orientation médicale initiée sur la plateforme MedScan.",
                "PENDING",
                null,
                seal,
                now,
                now
        );

        referralsById.put(referral.id(), referral);
        referralsByCode.put(code.toUpperCase(), referral);

        // Notification automatique au patient
        if (patient.linkedUserId() != null) {
            messagingService.dispatchNotification(
                    patient.linkedUserId(),
                    "PATIENT",
                    "REFERRAL_CREATED",
                    "URGENT".equalsIgnoreCase(referral.priority()) ? "WARNING" : "INFO",
                    "Nouvelle orientation médicale : " + referral.targetDepartment(),
                    "Le Dr. " + referral.referringDoctorName() + " vous a orienté vers " + referral.targetDepartment() +
                            " (" + referral.targetTenantName() + "). Réf: " + referral.referralCode(),
                    "MedicalReferral",
                    referral.id().toString(),
                    sourceTenantId
            );
        }

        // Notification automatique au médecin cible ou chef de service si spécifié
        if (targetDoctorId != null) {
            messagingService.dispatchNotification(
                    targetDoctorId,
                    "DOCTOR",
                    "REFERRAL_INCOMING",
                    "URGENT".equalsIgnoreCase(referral.priority()) ? "CRITICAL" : "INFO",
                    "Patient adressé : " + patient.fullName() + " (" + referral.priority() + ")",
                    "Nouvelle demande d'orientation reçue de Dr. " + referral.referringDoctorName() + " (" + referral.sourceTenantName() + ") : " + referral.reason(),
                    "MedicalReferral",
                    referral.id().toString(),
                    resolvedTargetTenantId
            );
        }

        clinicalService.logAudit(
                referringDoctorId,
                referringDoctorName != null ? referringDoctorName : "Médecin",
                "DOCTOR",
                sourceTenantId,
                "MEDICAL_REFERRAL_CREATED",
                "MedicalReferral",
                referral.id().toString(),
                "SUCCESS",
                "Orientation du patient " + patient.fullName() + " vers " + referral.targetDepartment() + " (" + referral.targetTenantName() + ") - Réf: " + code
        );

        return referral;
    }

    public Optional<MedicalReferral> findReferralById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(referralsById.get(id));
    }

    public Optional<MedicalReferral> findReferralByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(referralsByCode.get(code.trim().toUpperCase()));
    }

    public List<MedicalReferral> listReferrals(UUID tenantId, UUID patientId, UUID doctorId, Boolean incomingOnly) {
        return referralsById.values().stream()
                .filter(r -> patientId == null || r.patientId().equals(patientId))
                .filter(r -> doctorId == null || r.referringDoctorId().equals(doctorId) || (r.targetDoctorId() != null && r.targetDoctorId().equals(doctorId)))
                .filter(r -> {
                    if (tenantId == null) return true;
                    if (Boolean.TRUE.equals(incomingOnly)) {
                        return r.targetTenantId().equals(tenantId);
                    }
                    return r.sourceTenantId().equals(tenantId) || r.targetTenantId().equals(tenantId);
                })
                .sorted(Comparator.comparing(MedicalReferral::createdAt).reversed())
                .collect(Collectors.toList());
    }

    public MedicalReferral updateReferralStatus(UUID referralId, String newStatus, String rejectionReason, UUID actorId, String actorName, String actorRole, UUID actorTenantId) {
        MedicalReferral current = findReferralById(referralId)
                .orElseThrow(() -> new IllegalArgumentException("Orientation médicale introuvable: " + referralId));

        MedicalReferral updated = current.withStatus(newStatus, rejectionReason, Instant.now());
        referralsById.put(referralId, updated);
        referralsByCode.put(updated.referralCode().toUpperCase(), updated);

        // Alerte au médecin référent
        messagingService.dispatchNotification(
                current.referringDoctorId(),
                "DOCTOR",
                "REFERRAL_STATUS_UPDATED",
                "REJECTED".equalsIgnoreCase(newStatus) ? "WARNING" : "INFO",
                "Mise à jour orientation " + current.referralCode() + " : " + newStatus,
                "L'orientation vers " + current.targetDepartment() + " a été mise à jour par " + actorName + " : statut = " + newStatus +
                        (rejectionReason != null ? " (Motif: " + rejectionReason + ")" : ""),
                "MedicalReferral",
                referralId.toString(),
                current.sourceTenantId()
        );

        clinicalService.logAudit(
                actorId,
                actorName,
                actorRole,
                actorTenantId,
                "MEDICAL_REFERRAL_UPDATED",
                "MedicalReferral",
                referralId.toString(),
                "SUCCESS",
                "Statut de l'orientation " + current.referralCode() + " changé à " + newStatus
        );

        return updated;
    }

    private void initSeedReferrals() {
        UUID seedHospitalOuaga = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
        UUID seedClinicAlbies = UUID.fromString("f56de30f-c802-42c6-8587-707a8d1a9814");
        UUID seedDoctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
        UUID seedPatientFatouId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");

        UUID r1Id = UUID.fromString("d1111111-3333-5555-7777-999999999991");
        String code1 = "REF-2026-0001";
        Instant stampTime1 = Instant.now().minus(2, ChronoUnit.DAYS);

        HospitalSeal seal1 = new HospitalSeal(
                seedHospitalOuaga,
                "Centre Hospitalier Universitaire de Ouagadougou",
                "CH_OUAGADOUGOU",
                "Avenue de l'Hôpital, Ouagadougou",
                "+226 25 30 66 44",
                "contact@ch-ouaga.bf",
                "MS-SANTE-BF-REF-01",
                stampTime1,
                "Dr. Seydou Traore",
                "OM-BF-4412",
                HospitalSeal.computeVerificationHash(seedHospitalOuaga, code1, seedPatientFatouId, stampTime1, "Dr. Seydou Traore"),
                "https://medscan-sluw.onrender.com/medscan/api/v1/certificates/verify?code=" + code1
        );

        MedicalReferral ref1 = new MedicalReferral(
                r1Id,
                code1,
                seedPatientFatouId,
                "Fatou Ouedraogo",
                "+226 70 12 34 56",
                seedHospitalOuaga,
                "Centre Hospitalier Universitaire de Ouagadougou",
                "Médecine Interne & Infectiologie",
                seedDoctorId,
                "Dr. Seydou Traore",
                seedClinicAlbies,
                "Clinique Médico-Chirurgicale des Albies",
                "Pneumologie & Allergologie Spécialisée",
                null,
                "Dr. Aïssatou Sawadogo",
                "Asthme sévère réfractaire et épisodes dyspnéiques récurrents sous corticothérapie",
                "URGENT",
                "Patiente de 32 ans, antécédents d'asthme bronchique. Bilan biologique récent négatif pour pneumopathie aiguë mais saturation limite.",
                "ACCEPTED",
                null,
                seal1,
                stampTime1,
                Instant.now().minus(1, ChronoUnit.DAYS)
        );

        referralsById.put(r1Id, ref1);
        referralsByCode.put(code1, ref1);
    }
}
