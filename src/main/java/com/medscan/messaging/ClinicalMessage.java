package com.medscan.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Message clinique sécurisé entre acteurs de santé (MOD-15).
 * Permet les échanges directs (Médecin <-> Patient, Médecin <-> Pharmacien, Médecin <-> Technicien Labo).
 */
public record ClinicalMessage(
        UUID id,
        UUID conversationId,
        UUID tenantId,
        UUID senderId,
        String senderName,
        String senderRole,
        UUID recipientId,
        String recipientName,
        String recipientRole,
        UUID patientId,
        String subject,
        String content,
        String priority, // ROUTINE, URGENT, EMERGENCY
        Instant readAt,
        Instant sentAt
) {
    public ClinicalMessage withRead(Instant readTime) {
        return new ClinicalMessage(
                id,
                conversationId,
                tenantId,
                senderId,
                senderName,
                senderRole,
                recipientId,
                recipientName,
                recipientRole,
                patientId,
                subject,
                content,
                priority,
                readTime,
                sentAt
        );
    }
}
