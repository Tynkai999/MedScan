package com.medscan.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;

class MessagingNotificationServiceTest {

    private ClinicalService clinicalService;
    private MessagingNotificationService messagingService;

    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID PATIENT_USER_ID = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4"); // patient@medscan.org
    private static final UUID DOCTOR_USER_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");  // doctor@medscan.org

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        messagingService = new MessagingNotificationService(clinicalService);
    }

    @Test
    @DisplayName("Envoi de message clinique sécurisé avec déclenchement automatique d'une alerte/notification")
    void testSendMessageAndAutoNotification() {
        int initialUnread = messagingService.getUnreadNotificationCount(PATIENT_USER_ID);

        ClinicalMessage msg = messagingService.sendMessage(
                null, // nouvelle conversation
                DOCTOR_USER_ID,
                "Dr. Seydou Traore",
                "DOCTOR",
                PATIENT_USER_ID,
                "Fatou Ouedraogo",
                "PATIENT",
                UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"),
                "Recommandation traitement",
                "Prenez bien vos comprimés au cours des repas.",
                "ROUTINE",
                HOSPITAL_TENANT
        );

        assertNotNull(msg);
        assertNotNull(msg.conversationId());
        assertEquals("Prenez bien vos comprimés au cours des repas.", msg.content());

        // La patiente doit avoir reçu une nouvelle notification
        int newUnread = messagingService.getUnreadNotificationCount(PATIENT_USER_ID);
        assertEquals(initialUnread + 1, newUnread, "L'envoi d'un message doit incrémenter le compteur de notifications.");

        List<InAppNotification> notifs = messagingService.listNotificationsForUser(PATIENT_USER_ID, true);
        assertTrue(notifs.stream().anyMatch(n -> n.title().contains("Dr. Seydou Traore")));
    }

    @Test
    @DisplayName("Consultation du fil de discussion d'une conversation clinique")
    void testListMessagesInConversation() {
        UUID convId = UUID.fromString("f1111111-2222-3333-4444-555555555551");
        List<ClinicalMessage> thread = messagingService.listMessagesInConversation(convId);
        assertFalse(thread.isEmpty(), "Le fil de discussion seed doit contenir au moins 1 message.");
        assertTrue(thread.get(0).content().contains("paludisme"));
    }

    @Test
    @DisplayName("Cycle de vie des notifications : Lecture individuelle et marquage global")
    void testNotificationReadCycle() {
        List<InAppNotification> unreadBefore = messagingService.listNotificationsForUser(PATIENT_USER_ID, true);
        assertFalse(unreadBefore.isEmpty(), "La patiente doit avoir des notifications non lues.");

        InAppNotification first = unreadBefore.get(0);
        Optional<InAppNotification> read = messagingService.markNotificationAsRead(first.id(), PATIENT_USER_ID);
        assertTrue(read.isPresent());
        assertTrue(read.get().isRead());
        assertNotNull(read.get().readAt());

        // Marquage de toutes les notifications restantes comme lues
        int remainingMarked = messagingService.markAllNotificationsAsRead(PATIENT_USER_ID);
        assertTrue(remainingMarked >= 0);

        assertEquals(0, messagingService.getUnreadNotificationCount(PATIENT_USER_ID), "Toutes les notifications doivent être lues.");
    }

    @Test
    @DisplayName("Émission d'une alerte critique multi-entité (ex: alerte de laboratoire critique)")
    void testDispatchCriticalAlert() {
        InAppNotification alert = messagingService.dispatchNotification(
                DOCTOR_USER_ID,
                "DOCTOR",
                "CRITICAL_LAB_ALERT",
                "CRITICAL",
                "Alerte Anémie Sévère",
                "Taux d'hémoglobine inférieur à 7.0 g/dL pour le patient Fatou Ouedraogo",
                "LabOrder",
                "LAB-2026-9999",
                HOSPITAL_TENANT
        );

        assertNotNull(alert);
        assertEquals("CRITICAL", alert.severity());
        assertEquals("CRITICAL_LAB_ALERT", alert.category());
        assertFalse(alert.isRead());
    }

    @Test
    @DisplayName("Liste des conversations groupées et ordonnées pour un utilisateur")
    void testListConversationsForUser() {
        var convs = messagingService.listConversationsForUser(DOCTOR_USER_ID);
        assertFalse(convs.isEmpty(), "Le médecin doit avoir au moins une conversation active.");
        assertEquals("Fatou Ouedraogo", convs.get(0).participantName());
        assertEquals("PATIENT", convs.get(0).participantRole());
        assertNotNull(convs.get(0).lastMessage());

        var patientConvs = messagingService.listConversationsForUser(PATIENT_USER_ID);
        assertFalse(patientConvs.isEmpty(), "La patiente doit avoir sa conversation avec le médecin.");
        assertEquals("Dr. Seydou Traore", patientConvs.get(0).participantName());
        assertEquals("DOCTOR", patientConvs.get(0).participantRole());
    }

    @Test
    @DisplayName("Isolation des contacts éligibles selon les consultations cliniques effectives")
    void testListEligibleContactsIsolation() {
        // Patient Fatou ne voit que les praticiens consultés
        var patientContacts = messagingService.listEligibleContacts(PATIENT_USER_ID, "PATIENT");
        assertFalse(patientContacts.isEmpty(), "La patiente doit voir les praticiens consultés.");
        assertTrue(patientContacts.stream().allMatch(c -> "DOCTOR".equals(c.role())));

        // Docteur ne voit que les patients consultés
        var doctorContacts = messagingService.listEligibleContacts(DOCTOR_USER_ID, "DOCTOR");
        assertFalse(doctorContacts.isEmpty(), "Le médecin doit voir les patients qu'il a consultés.");
        assertTrue(doctorContacts.stream().allMatch(c -> "PATIENT".equals(c.role())));
    }
}
