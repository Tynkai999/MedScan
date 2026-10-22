package com.medscan.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification et alerte universelle multi-entités de l'application (MOD-15).
 * Diffusée aux Patients, Médecins, Pharmaciens, Techniciens Labo, Livreurs et Administrateurs.
 */
public record InAppNotification(
        UUID id,
        UUID tenantId,
        UUID recipientUserId,
        String recipientRole,
        String category,       // CRITICAL_LAB_ALERT, APPOINTMENT_REMINDER, EPIDEMIC_ALERT, NEW_PRESCRIPTION, DELIVERY_UPDATE, NEW_MESSAGE
        String severity,       // INFO, WARNING, CRITICAL
        String title,
        String message,
        String referenceEntityType,
        String referenceEntityId,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
    public InAppNotification withRead(Instant readTime) {
        return new InAppNotification(
                id,
                tenantId,
                recipientUserId,
                recipientRole,
                category,
                severity,
                title,
                message,
                referenceEntityType,
                referenceEntityId,
                true,
                readTime,
                createdAt
        );
    }
}
