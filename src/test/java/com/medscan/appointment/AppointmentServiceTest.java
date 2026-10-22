package com.medscan.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;

class AppointmentServiceTest {

    private ClinicalService clinicalService;
    private AppointmentService appointmentService;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"); // Fatou Ouedraogo
    private static final UUID DOCTOR_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");  // Dr Seydou Traore
    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        appointmentService = new AppointmentService(clinicalService);
    }

    @Test
    @DisplayName("Planification d'un rendez-vous de téléconsultation avec génération du salon vidéo")
    void testBookTeleconsultationAppointment() {
        Appointment appt = appointmentService.bookAppointment(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "DOCTOR",
                "TELECONSULTATION",
                Instant.now().plus(3, ChronoUnit.DAYS),
                30,
                "Avis sur adaptation traitement asthme",
                DOCTOR_ID,
                "dr.traore",
                "DOCTOR",
                HOSPITAL_TENANT
        );

        assertNotNull(appt);
        assertEquals("REQUESTED", appt.status());
        assertEquals("TELECONSULTATION", appt.appointmentType());
        assertNotNull(appt.teleconsultationRoomId(), "Le salon de téléconsultation doit être généré.");
        assertTrue(appt.teleconsultationJoinUrl().contains("meet.jit.si"), "L'URL de téléconsultation doit être disponible.");
    }

    @Test
    @DisplayName("Cycle de vie du rendez-vous : Confirmation puis clôture clinique")
    void testAppointmentLifecycleConfirmationAndCompletion() {
        Appointment appt = appointmentService.bookAppointment(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "DOCTOR",
                "IN_PERSON",
                Instant.now().plus(1, ChronoUnit.DAYS),
                45,
                "Consultation présentielle de routine",
                DOCTOR_ID,
                "dr.traore",
                "DOCTOR",
                HOSPITAL_TENANT
        );

        // 1. Confirmation
        Appointment confirmed = appointmentService.confirmAppointment(
                appt.id(), DOCTOR_ID, "dr.traore", "DOCTOR", HOSPITAL_TENANT
        );
        assertEquals("CONFIRMED", confirmed.status());

        // 2. Clôture avec notes
        Appointment completed = appointmentService.completeAppointment(
                appt.id(), "Patient examiné, état stationnaire.", DOCTOR_ID, "dr.traore", "DOCTOR", HOSPITAL_TENANT
        );
        assertEquals("COMPLETED", completed.status());
        assertEquals("Patient examiné, état stationnaire.", completed.clinicalNotes());
    }

    @Test
    @DisplayName("Annulation d'un rendez-vous avec traçabilité du motif")
    void testCancelAppointment() {
        Appointment appt = appointmentService.bookAppointment(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "DOCTOR",
                "LAB_ANALYSIS",
                Instant.now().plus(5, ChronoUnit.DAYS),
                15,
                "Prélèvement sanguin",
                DOCTOR_ID,
                "dr.traore",
                "DOCTOR",
                HOSPITAL_TENANT
        );

        Appointment cancelled = appointmentService.cancelAppointment(
                appt.id(), "Empêchement professionnel de la patiente",
                PATIENT_ID, "patient@medscan.org", "PATIENT", HOSPITAL_TENANT
        );

        assertEquals("CANCELLED", cancelled.status());
        assertTrue(cancelled.clinicalNotes().contains("Empêchement"));
    }

    @Test
    @DisplayName("Filtrage des rendez-vous par patient et par praticien")
    void testFilterAppointments() {
        List<Appointment> patientAppts = appointmentService.listAppointmentsForPatient(PATIENT_ID);
        assertTrue(patientAppts.size() >= 2, "La patiente de seed doit posséder au moins 2 RDV.");

        List<Appointment> doctorAppts = appointmentService.listAppointmentsForPractitioner(DOCTOR_ID);
        assertTrue(doctorAppts.size() >= 1, "Le médecin de seed doit posséder au moins 1 RDV.");
    }
}
