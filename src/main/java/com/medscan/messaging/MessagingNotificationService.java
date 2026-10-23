package com.medscan.messaging;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;

/**
 * Service central de Messagerie Sécurisée & Notifications d'Alertes Multi-Entités (MOD-15).
 * Connecte toutes les entités de l'écosystème MedScan (Médecin, Patient, Laboratoire, Pharmacie, Livreur).
 */
public class MessagingNotificationService {

    private final ClinicalService clinicalService;
    private final Map<UUID, ClinicalMessage> messagesById = new ConcurrentHashMap<>();
    private final Map<UUID, List<ClinicalMessage>> messagesByConversationId = new ConcurrentHashMap<>();
    private final Map<UUID, List<InAppNotification>> notificationsByUserId = new ConcurrentHashMap<>();

    public MessagingNotificationService(ClinicalService clinicalService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
        initSeedMessagesAndNotifications();
    }

    public MessagingNotificationService() {
        this(new ClinicalService());
    }

    // ==========================================
    // MESSAGERIE SÉCURISÉE CLINIQUE
    // ==========================================

    public ClinicalMessage sendMessage(UUID conversationId, UUID senderId, String senderName, String senderRole,
                                       UUID recipientId, String recipientName, String recipientRole,
                                       UUID patientId, String subject, String content, String priority,
                                       UUID tenantId) {
        if (senderId == null || recipientId == null || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Expéditeur, destinataire et contenu sont obligatoires.");
        }

        UUID convId = (conversationId != null) ? conversationId : UUID.randomUUID();
        UUID resolvedTenant = tenantId != null ? tenantId : (patientId != null ? clinicalService.findPatientById(patientId).map(Patient::tenantId).orElse(null) : null);

        ClinicalMessage message = new ClinicalMessage(
                UUID.randomUUID(),
                convId,
                resolvedTenant,
                senderId,
                senderName != null ? senderName : "Expéditeur",
                senderRole != null ? senderRole : "USER",
                recipientId,
                recipientName != null ? recipientName : "Destinataire",
                recipientRole != null ? recipientRole : "USER",
                patientId,
                subject != null ? subject : "Message clinique",
                content,
                priority != null ? priority.toUpperCase() : "ROUTINE",
                null,
                Instant.now()
        );

        messagesById.put(message.id(), message);
        messagesByConversationId.computeIfAbsent(convId, k -> new CopyOnWriteArrayList<>()).add(message);

        // Déclenchement automatique d'une notification pour le destinataire
        dispatchNotification(
                recipientId,
                recipientRole,
                "NEW_MESSAGE",
                "URGENT".equalsIgnoreCase(priority) || "EMERGENCY".equalsIgnoreCase(priority) ? "WARNING" : "INFO",
                "Nouveau message de " + message.senderName(),
                message.content().length() > 80 ? message.content().substring(0, 77) + "..." : message.content(),
                "ClinicalMessage",
                message.id().toString(),
                message.tenantId()
        );

        clinicalService.logAudit(senderId, senderName, senderRole, message.tenantId(),
                "MESSAGE_SENT", "ClinicalMessage", message.id().toString(), "SUCCESS",
                "Envoi de message sécurisé à " + message.recipientName() + " (" + message.recipientRole() + ")");

        return message;
    }

    public List<ClinicalMessage> listMessagesInConversation(UUID conversationId) {
        return messagesByConversationId.getOrDefault(conversationId, Collections.emptyList()).stream()
                .sorted(Comparator.comparing(ClinicalMessage::sentAt))
                .collect(Collectors.toList());
    }

    public List<ClinicalMessage> listMessagesWithPeer(UUID user1, UUID user2) {
        return messagesById.values().stream()
                .filter(m -> (m.senderId().equals(user1) && m.recipientId().equals(user2)) ||
                             (m.senderId().equals(user2) && m.recipientId().equals(user1)))
                .sorted(Comparator.comparing(ClinicalMessage::sentAt))
                .collect(Collectors.toList());
    }

    public List<ClinicalMessage> listUserMessages(UUID userId) {
        return messagesById.values().stream()
                .filter(m -> m.senderId().equals(userId) || m.recipientId().equals(userId))
                .sorted(Comparator.comparing(ClinicalMessage::sentAt).reversed())
                .collect(Collectors.toList());
    }

    // ==========================================
    // NOTIFICATIONS & ALERTES MULTI-ENTITÉS
    // ==========================================

    public InAppNotification dispatchNotification(UUID recipientUserId, String recipientRole, String category,
                                                 String severity, String title, String messageText,
                                                 String referenceEntityType, String referenceEntityId,
                                                 UUID tenantId) {
        if (recipientUserId == null || title == null) {
            throw new IllegalArgumentException("Destinataire et titre de notification requis.");
        }

        InAppNotification notif = new InAppNotification(
                UUID.randomUUID(),
                tenantId,
                recipientUserId,
                recipientRole != null ? recipientRole : "USER",
                category != null ? category.toUpperCase() : "GENERAL_INFO",
                severity != null ? severity.toUpperCase() : "INFO",
                title,
                messageText != null ? messageText : "",
                referenceEntityType,
                referenceEntityId,
                false,
                null,
                Instant.now()
        );

        notificationsByUserId.computeIfAbsent(recipientUserId, k -> new CopyOnWriteArrayList<>()).add(0, notif);
        return notif;
    }

    public List<InAppNotification> listNotificationsForUser(UUID userId, boolean unreadOnly) {
        return notificationsByUserId.getOrDefault(userId, Collections.emptyList()).stream()
                .filter(n -> !unreadOnly || !n.isRead())
                .sorted(Comparator.comparing(InAppNotification::createdAt).reversed())
                .collect(Collectors.toList());
    }

    public int getUnreadNotificationCount(UUID userId) {
        return (int) notificationsByUserId.getOrDefault(userId, Collections.emptyList()).stream()
                .filter(n -> !n.isRead())
                .count();
    }

    public Optional<InAppNotification> markNotificationAsRead(UUID notificationId, UUID actorUserId) {
        List<InAppNotification> list = notificationsByUserId.get(actorUserId);
        if (list == null) return Optional.empty();

        for (int i = 0; i < list.size(); i++) {
            InAppNotification n = list.get(i);
            if (n.id().equals(notificationId)) {
                InAppNotification read = n.withRead(Instant.now());
                list.set(i, read);
                return Optional.of(read);
            }
        }
        return Optional.empty();
    }

    public int markAllNotificationsAsRead(UUID actorUserId) {
        List<InAppNotification> list = notificationsByUserId.get(actorUserId);
        if (list == null) return 0;

        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            InAppNotification n = list.get(i);
            if (!n.isRead()) {
                list.set(i, n.withRead(Instant.now()));
                count++;
            }
        }
        return count;
    }

    private void initSeedMessagesAndNotifications() {
        UUID seedHospitalTenant = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
        UUID seedDoctorUserId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
        UUID seedPatientUserId = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4");
        UUID seedPatientId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
        UUID conv1 = UUID.fromString("f1111111-2222-3333-4444-555555555551");

        // Message 1 : Dr Seydou Traoré -> Fatou Ouedraogo
        ClinicalMessage m1 = new ClinicalMessage(
                UUID.fromString("e1111111-2222-3333-4444-555555555551"),
                conv1,
                seedHospitalTenant,
                seedDoctorUserId,
                "Dr. Seydou Traore",
                "DOCTOR",
                seedPatientUserId,
                "Fatou Ouedraogo",
                "PATIENT",
                seedPatientId,
                "Résultats de votre analyse et consignes",
                "Bonjour Mme Ouedraogo, votre goutte épaisse confirme un paludisme débutant. Commencez le traitement prescrit immédiatement et hydratez-vous abondamment.",
                "URGENT",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS)
        );
        messagesById.put(m1.id(), m1);
        messagesByConversationId.computeIfAbsent(conv1, k -> new CopyOnWriteArrayList<>()).add(m1);

        // Notifications de départ pour le médecin
        dispatchNotification(
                seedDoctorUserId,
                "DOCTOR",
                "CRITICAL_LAB_ALERT",
                "CRITICAL",
                "Alerte Laboratoire : Paludisme confirmé pour Fatou Ouedraogo",
                "Goutte épaisse positive (12 500 trophozoïtes/µL). Veuillez ajuster la prescription.",
                "LabOrder",
                "LAB-2026-0042",
                seedHospitalTenant
        );

        // Notification de rappel de rendez-vous pour la patiente
        dispatchNotification(
                seedPatientUserId,
                "PATIENT",
                "APPOINTMENT_REMINDER",
                "INFO",
                "Rappel : Téléconsultation dans 2 jours",
                "Votre rendez-vous vidéo avec le Dr Seydou Traore est prévu le 24 octobre à 10h00.",
                "Appointment",
                "c1111111-2222-3333-4444-555555555551",
                seedHospitalTenant
        );

        // Notification de colis prêt pour la patiente
        dispatchNotification(
                seedPatientUserId,
                "PATIENT",
                "DELIVERY_UPDATE",
                "INFO",
                "Livraison en cours : Vos médicaments arrivent",
                "Le livreur a pris en charge votre colis scellé en pharmacie. Préparez votre code secret OTP.",
                "DeliveryMission",
                "DEL-2026-0001",
                seedHospitalTenant
        );
    }
}
