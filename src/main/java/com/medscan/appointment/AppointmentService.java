package com.medscan.appointment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;

/**
 * Service de gestion des Rendez-vous et Téléconsultations (MOD-12).
 * Couvre l'ensemble des acteurs de santé : Médecins, Infirmiers, Techniciens de Laboratoire, Radiologues.
 */
public class AppointmentService {

    private final ClinicalService clinicalService;
    private final Map<UUID, Appointment> appointmentsById = new ConcurrentHashMap<>();
    private final Map<UUID, List<Appointment>> appointmentsByPatientId = new ConcurrentHashMap<>();
    private final Map<UUID, List<Appointment>> appointmentsByPractitionerId = new ConcurrentHashMap<>();

    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID PATIENT_FATOU_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
    private static final UUID DOCTOR_TRAORE_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
    private static final UUID LAB_TECH_ID = UUID.fromString("1e4a2c5b-8d3f-4e6a-9b7c-0a1d2e3f4a5b");

    public AppointmentService(ClinicalService clinicalService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
        initSeedAppointments();
    }

    public AppointmentService() {
        this(new ClinicalService());
    }

    /**
     * Planifie un nouveau rendez-vous (présentiel, téléconsultation, laboratoire, etc.).
     */
    public Appointment bookAppointment(UUID patientId, UUID practitionerId, String practitionerName,
                                       String practitionerRole, String appointmentType, Instant scheduledAt,
                                       int durationMinutes, String reason, UUID actorId, String actorUsername,
                                       String actorRole, UUID actorTenantId) {
        if (patientId == null || practitionerId == null || scheduledAt == null) {
            throw new IllegalArgumentException("Patient, praticien et horaire sont requis pour planifier un rendez-vous.");
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable: " + patientId));

        String roomToken = null;
        String joinUrl = null;
        if ("TELECONSULTATION".equalsIgnoreCase(appointmentType)) {
            roomToken = "medscan-tele-" + UUID.randomUUID().toString().substring(0, 8);
            joinUrl = "https://meet.jit.si/" + roomToken + "#config.prejoinPageEnabled=false";
        }

        Appointment appt = new Appointment(
                UUID.randomUUID(),
                actorTenantId != null ? actorTenantId : HOSPITAL_TENANT,
                patientId,
                patient.fullName(),
                practitionerId,
                practitionerName != null ? practitionerName : "Praticien MedScan",
                practitionerRole != null ? practitionerRole : "DOCTOR",
                appointmentType != null ? appointmentType.toUpperCase() : "IN_PERSON",
                scheduledAt,
                durationMinutes > 0 ? durationMinutes : 30,
                "REQUESTED",
                reason,
                null,
                roomToken,
                joinUrl,
                Instant.now(),
                Instant.now()
        );

        saveAppointment(appt);

        clinicalService.logAudit(actorId, actorUsername, actorRole, appt.tenantId(),
                "APPOINTMENT_BOOKED", "Appointment", appt.id().toString(), "SUCCESS",
                "Rendez-vous planifié pour " + patient.fullName() + " avec " + appt.practitionerName() + " (" + appt.appointmentType() + ")");

        return appt;
    }

    /**
     * Confirme un rendez-vous (par le praticien ou l'accueil médical).
     */
    public Appointment confirmAppointment(UUID appointmentId, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        Appointment existing = getAppointmentOrThrow(appointmentId);
        Appointment updated = existing.withStatus("CONFIRMED", "Rendez-vous validé par le secrétariat médical");
        saveAppointment(updated);

        clinicalService.logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "APPOINTMENT_CONFIRMED", "Appointment", appointmentId.toString(), "SUCCESS",
                "Confirmation du rendez-vous " + appointmentId);

        return updated;
    }

    /**
     * Annule un rendez-vous avec motif.
     */
    public Appointment cancelAppointment(UUID appointmentId, String cancelReason, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        Appointment existing = getAppointmentOrThrow(appointmentId);
        Appointment updated = existing.withStatus("CANCELLED", "Annulé: " + cancelReason);
        saveAppointment(updated);

        clinicalService.logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "APPOINTMENT_CANCELLED", "Appointment", appointmentId.toString(), "SUCCESS",
                "Annulation du rendez-vous " + appointmentId + " (Motif: " + cancelReason + ")");

        return updated;
    }

    /**
     * Clôture un rendez-vous après consultation ou examen.
     */
    public Appointment completeAppointment(UUID appointmentId, String notes, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        Appointment existing = getAppointmentOrThrow(appointmentId);
        Appointment updated = existing.withStatus("COMPLETED", notes);
        saveAppointment(updated);

        clinicalService.logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "APPOINTMENT_COMPLETED", "Appointment", appointmentId.toString(), "SUCCESS",
                "Rendez-vous honoré et clôturé (" + appointmentId + ")");

        return updated;
    }

    public Optional<Appointment> findAppointmentById(UUID id) {
        return Optional.ofNullable(appointmentsById.get(id));
    }

    public List<Appointment> listAppointmentsForPatient(UUID patientId) {
        return appointmentsByPatientId.getOrDefault(patientId, Collections.emptyList()).stream()
                .sorted(Comparator.comparing(Appointment::scheduledAt))
                .collect(Collectors.toList());
    }

    public List<Appointment> listAppointmentsForPractitioner(UUID practitionerId) {
        return appointmentsByPractitionerId.getOrDefault(practitionerId, Collections.emptyList()).stream()
                .sorted(Comparator.comparing(Appointment::scheduledAt))
                .collect(Collectors.toList());
    }

    public List<Appointment> listAllAppointments(UUID tenantId) {
        return appointmentsById.values().stream()
                .filter(a -> tenantId == null || a.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(Appointment::scheduledAt))
                .collect(Collectors.toList());
    }

    private Appointment getAppointmentOrThrow(UUID id) {
        Appointment a = appointmentsById.get(id);
        if (a == null) {
            throw new IllegalArgumentException("Rendez-vous introuvable: " + id);
        }
        return a;
    }

    private void saveAppointment(Appointment appt) {
        appointmentsById.put(appt.id(), appt);

        // Index patient
        appointmentsByPatientId.computeIfAbsent(appt.patientId(), k -> new CopyOnWriteArrayList<>());
        appointmentsByPatientId.get(appt.patientId()).removeIf(a -> a.id().equals(appt.id()));
        appointmentsByPatientId.get(appt.patientId()).add(appt);

        // Index praticien
        appointmentsByPractitionerId.computeIfAbsent(appt.practitionerId(), k -> new CopyOnWriteArrayList<>());
        appointmentsByPractitionerId.get(appt.practitionerId()).removeIf(a -> a.id().equals(appt.id()));
        appointmentsByPractitionerId.get(appt.practitionerId()).add(appt);
    }

    private void initSeedAppointments() {
        // 1. Téléconsultation de suivi Fatou Ouedraogo avec Dr Seydou Traore
        Appointment a1 = new Appointment(
                UUID.fromString("c1111111-2222-3333-4444-555555555551"),
                HOSPITAL_TENANT,
                PATIENT_FATOU_ID,
                "Fatou Ouedraogo",
                DOCTOR_TRAORE_ID,
                "Dr. Seydou Traore",
                "DOCTOR",
                "TELECONSULTATION",
                Instant.now().plus(2, ChronoUnit.DAYS),
                30,
                "CONFIRMED",
                "Suivi d'asthme et adaptation du traitement de fond",
                "Patient stable sous Salbutamol de crise",
                "medscan-tele-fatou-2026",
                "https://meet.jit.si/medscan-tele-fatou-2026#config.prejoinPageEnabled=false",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now()
        );
        saveAppointment(a1);

        // 2. Rendez-vous de prélèvement Laboratoire pour analyse sanguine
        Appointment a2 = new Appointment(
                UUID.fromString("c1111111-2222-3333-4444-555555555552"),
                HOSPITAL_TENANT,
                PATIENT_FATOU_ID,
                "Fatou Ouedraogo",
                LAB_TECH_ID,
                "Tech. Ousmane Sanou",
                "LAB_TECHNICIAN",
                "LAB_ANALYSIS",
                Instant.now().plus(4, ChronoUnit.DAYS),
                20,
                "REQUESTED",
                "Bilan sanguin complet & Goutte épaisse Paludisme",
                "Venir à jeun le matin",
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        saveAppointment(a2);
    }
}
