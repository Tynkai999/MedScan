package com.medscan.grievance;

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

import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;
import com.medscan.messaging.MessagingNotificationService;
import com.medscan.security.Role;
import com.medscan.security.service.ActorAccount;
import com.medscan.security.service.SeedUserRegistry;
import com.medscan.security.tenant.Tenant;
import com.medscan.security.tenant.TenantRegistry;

/**
 * Service de gestion des avis, signalements et réclamations des patients (MOD-18).
 * Transmet directement les alertes et retours d'expérience à l'administrateur de l'hôpital (TENANT_ADMIN)
 * pour le contrôle de la qualité, la déontologie et la résolution des litiges.
 */
public class GrievanceService {

    private final ClinicalService clinicalService;
    private final TenantRegistry tenantRegistry;
    private final MessagingNotificationService messagingService;
    private final SeedUserRegistry userRegistry;

    private final Map<UUID, PatientGrievance> grievancesById = new ConcurrentHashMap<>();
    private final Map<String, PatientGrievance> grievancesByCode = new ConcurrentHashMap<>();
    private final AtomicInteger grievanceCounter = new AtomicInteger(100);

    public GrievanceService(ClinicalService clinicalService, TenantRegistry tenantRegistry,
                            MessagingNotificationService messagingService, SeedUserRegistry userRegistry) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
        this.tenantRegistry = (tenantRegistry != null) ? tenantRegistry : new TenantRegistry();
        this.messagingService = (messagingService != null) ? messagingService : new MessagingNotificationService(this.clinicalService);
        this.userRegistry = (userRegistry != null) ? userRegistry : new SeedUserRegistry();
        initSeedGrievances();
    }

    public GrievanceService() {
        this(new ClinicalService(), new TenantRegistry(), new MessagingNotificationService(), new SeedUserRegistry());
    }

    public PatientGrievance submitGrievance(
            UUID patientId,
            UUID doctorId,
            String doctorName,
            UUID tenantId,
            UUID consultationId,
            String grievanceType,
            String category,
            int rating,
            String title,
            String description
    ) {
        if (patientId == null) {
            throw new IllegalArgumentException("L'identifiant du patient est obligatoire.");
        }
        if (doctorId == null) {
            throw new IllegalArgumentException("L'identifiant du médecin concerné est obligatoire.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre du signalement ou de l'avis est obligatoire.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("La description détaillée est obligatoire.");
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable: " + patientId));

        // Résolution dynamique du médecin et de sa structure de rattachement
        UUID resolvedTenantId = tenantId;
        String resolvedDoctorName = doctorName;
        if (resolvedTenantId == null || resolvedDoctorName == null || resolvedDoctorName.isBlank()) {
            Optional<ActorAccount> docAccount = userRegistry.findById(doctorId);
            if (docAccount.isPresent()) {
                if (resolvedTenantId == null) resolvedTenantId = docAccount.get().tenantId();
                if (resolvedDoctorName == null || resolvedDoctorName.isBlank()) resolvedDoctorName = docAccount.get().displayName();
            }
        }
        if (resolvedTenantId == null) {
            resolvedTenantId = patient.tenantId();
        }
        if (resolvedDoctorName == null || resolvedDoctorName.isBlank()) {
            resolvedDoctorName = "Dr. Médecin Praticien";
        }

        Tenant tenant = tenantRegistry.findById(resolvedTenantId).orElse(null);
        String hospitalName = tenant != null ? tenant.name() : tenantRegistry.resolveTenantDisplayName(resolvedTenantId);

        int safeRating = Math.max(1, Math.min(5, rating > 0 ? rating : 3));
        String safeType = (grievanceType != null && !grievanceType.isBlank()) ? grievanceType.trim().toUpperCase() : "COMPLAINT";
        String safeCat = (category != null && !category.isBlank()) ? category.trim().toUpperCase() : "CARE_QUALITY";

        // Détection automatique de la sévérité
        String severity = "LOW";
        if (safeRating <= 2 || "ETHICAL_CONCERN".equalsIgnoreCase(safeCat) || "INCIDENT".equalsIgnoreCase(safeType)) {
            severity = safeRating == 1 ? "CRITICAL" : "HIGH";
        } else if (safeRating == 3) {
            severity = "MEDIUM";
        }

        String code = "GRV-2026-" + String.format("%04d", grievanceCounter.incrementAndGet());
        Instant now = Instant.now();

        PatientGrievance grievance = new PatientGrievance(
                UUID.randomUUID(),
                code,
                patientId,
                patient.fullName(),
                patient.phone(),
                doctorId,
                resolvedDoctorName,
                resolvedTenantId,
                hospitalName,
                consultationId,
                safeType,
                safeCat,
                safeRating,
                severity,
                title.trim(),
                description.trim(),
                "SUBMITTED",
                null,
                null,
                null,
                null,
                null,
                now
        );

        grievancesById.put(grievance.id(), grievance);
        grievancesByCode.put(code.toUpperCase(), grievance);

        // Acheminement direct à l'administrateur de l'hôpital (TENANT_ADMIN)
        List<ActorAccount> tenantAdmins = userRegistry.findByTenantId(resolvedTenantId).stream()
                .filter(u -> u.roles().contains(Role.TENANT_ADMIN.roleName()) || u.roles().contains(Role.SUPER_ADMIN.roleName()))
                .collect(Collectors.toList());

        for (ActorAccount admin : tenantAdmins) {
            messagingService.dispatchNotification(
                    admin.userId(),
                    "TENANT_ADMIN",
                    "PATIENT_GRIEVANCE_FILED",
                    "CRITICAL".equalsIgnoreCase(severity) ? "CRITICAL" : "WARNING",
                    "Alerte Qualité : Réclamation patient sur " + resolvedDoctorName,
                    "Le patient " + patient.fullName() + " a déposé une réclamation (" + safeCat + " - " + safeRating + "★) : \"" +
                            grievance.title() + "\". Réf: " + code,
                    "PatientGrievance",
                    grievance.id().toString(),
                    resolvedTenantId
            );
        }

        // Accusé de réception pour le patient
        if (patient.linkedUserId() != null) {
            messagingService.dispatchNotification(
                    patient.linkedUserId(),
                    "PATIENT",
                    "GRIEVANCE_SUBMITTED",
                    "INFO",
                    "Signalement enregistré et transmis à la Direction Médicale",
                    "Votre avis/réclamation concernant le Dr. " + resolvedDoctorName +
                            " a bien été transmis à la direction de " + hospitalName + ". Réf: " + code,
                    "PatientGrievance",
                    grievance.id().toString(),
                    resolvedTenantId
            );
        }

        clinicalService.logAudit(
                patientId,
                patient.fullName(),
                "PATIENT",
                resolvedTenantId,
                "PATIENT_GRIEVANCE_SUBMITTED",
                "PatientGrievance",
                grievance.id().toString(),
                "CRITICAL".equalsIgnoreCase(severity) ? "WARNING" : "SUCCESS",
                "Réclamation patient soumise contre Dr. " + resolvedDoctorName + " (" + safeCat + ") - Réf: " + code
        );

        return grievance;
    }

    public Optional<PatientGrievance> findGrievanceById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(grievancesById.get(id));
    }

    public Optional<PatientGrievance> findGrievanceByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(grievancesByCode.get(code.trim().toUpperCase()));
    }

    public List<PatientGrievance> listGrievances(UUID requesterTenantId, boolean isPlatformAdmin, UUID patientId, UUID doctorId, String statusFilter) {
        return grievancesById.values().stream()
                .filter(g -> patientId == null || g.patientId().equals(patientId))
                .filter(g -> doctorId == null || g.doctorId().equals(doctorId))
                .filter(g -> isPlatformAdmin || requesterTenantId == null || g.tenantId().equals(requesterTenantId))
                .filter(g -> statusFilter == null || statusFilter.isBlank() || g.status().equalsIgnoreCase(statusFilter.trim()))
                .sorted(Comparator.comparing(PatientGrievance::createdAt).reversed())
                .collect(Collectors.toList());
    }

    public PatientGrievance reviewGrievance(
            UUID grievanceId,
            UUID adminId,
            String adminName,
            String newStatus,
            String adminNotes,
            String actionTaken
    ) {
        PatientGrievance current = findGrievanceById(grievanceId)
                .orElseThrow(() -> new IllegalArgumentException("Réclamation introuvable avec l'ID: " + grievanceId));

        String effectiveStatus = (newStatus != null && !newStatus.isBlank()) ? newStatus.trim().toUpperCase() : "RESOLVED";
        Instant now = Instant.now();

        PatientGrievance reviewed = current.withReview(
                effectiveStatus,
                adminNotes != null ? adminNotes.trim() : current.adminNotes(),
                actionTaken != null ? actionTaken.trim() : current.adminActionTaken(),
                adminId,
                adminName != null ? adminName.trim() : "Direction Hospitalière",
                now
        );

        grievancesById.put(grievanceId, reviewed);
        grievancesByCode.put(reviewed.grievanceCode().toUpperCase(), reviewed);

        // Notification de retour au patient
        clinicalService.findPatientById(current.patientId()).ifPresent(p -> {
            if (p.linkedUserId() != null) {
                messagingService.dispatchNotification(
                        p.linkedUserId(),
                        "PATIENT",
                        "GRIEVANCE_RESOLVED",
                        "INFO",
                        "Réponse de la direction hospitalière à votre réclamation",
                        "Votre réclamation " + current.grievanceCode() + " a été traitée par la direction de " + current.hospitalName() +
                                ". Mesure prise : " + (reviewed.adminActionTaken() != null ? reviewed.adminActionTaken() : "Dossier clôturé"),
                        "PatientGrievance",
                        grievanceId.toString(),
                        current.tenantId()
                );
            }
        });

        clinicalService.logAudit(
                adminId,
                adminName != null ? adminName : "Administrateur Hôpital",
                "TENANT_ADMIN",
                current.tenantId(),
                "PATIENT_GRIEVANCE_REVIEWED",
                "PatientGrievance",
                grievanceId.toString(),
                "SUCCESS",
                "Réclamation " + current.grievanceCode() + " traitée par la direction. Nouveau statut: " + effectiveStatus
        );

        return reviewed;
    }

    private void initSeedGrievances() {
        UUID seedHospitalOuaga = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
        UUID seedDoctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
        UUID seedPatientFatouId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
        UUID seedAdminId = UUID.fromString("28856dce-4f9b-4850-b26a-4126b29faf32"); // tenantadmin@medscan.org

        UUID g1Id = UUID.fromString("a3333333-5555-7777-9999-111111111111");
        String code1 = "GRV-2026-0001";
        Instant time1 = Instant.now().minus(3, ChronoUnit.DAYS);

        PatientGrievance g1 = new PatientGrievance(
                g1Id,
                code1,
                seedPatientFatouId,
                "Fatou Ouedraogo",
                "+226 70 12 34 56",
                seedDoctorId,
                "Dr. Seydou Traore",
                seedHospitalOuaga,
                "Centre Hospitalier Universitaire de Ouagadougou",
                null,
                "FEEDBACK",
                "DELAY_WAIT_TIME",
                3,
                "MEDIUM",
                "Retard important lors de la consultation sans information préalable",
                "Prise en charge médicale excellente par le médecin, mais plus de 2 heures d'attente en salle sans explication de l'accueil.",
                "RESOLVED",
                "Signalement légitime. Incident survenu lors d'une urgence réanimatoire imprévue mobilisant le praticien.",
                "Mise en place d'un SMS automatique informant les patients en cas de décalage des consultations.",
                seedAdminId,
                "Mamadou Sawadogo (Directeur CHU Ouaga)",
                Instant.now().minus(1, ChronoUnit.DAYS),
                time1
        );

        grievancesById.put(g1Id, g1);
        grievancesByCode.put(code1, g1);
    }
}
