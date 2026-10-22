package com.medscan.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * Commande de livraison sécurisée de produits pharmaceutiques (MOD-09).
 * Inclut la traçabilité complète du colis, la liaison avec l'ordonnance médicale,
 * et la preuve de livraison par code OTP cryptographique à 6 chiffres.
 */
public record DeliveryOrder(
        UUID id,
        String deliveryCode,
        String prescriptionCode,
        UUID prescriptionId,
        UUID patientId,
        String patientName,
        String patientPhone,
        String deliveryAddress,
        String destinationCity,
        UUID pharmacyTenantId,
        String pharmacyName,
        UUID logisticsTenantId,
        UUID deliveryAgentId,
        String deliveryAgentName,
        DeliveryStatus status,
        String packageDescription,
        String specialInstructions,
        String otpHash,
        String otpSalt,
        Instant otpExpiresAt,
        int otpAttemptsRemaining,
        String otpClear,
        Instant createdAt,
        Instant assignedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        String recipientConfirmation,
        String failureReason
) {

    public DeliveryOrder withAssignment(UUID agentId, String agentName, Instant when) {
        return new DeliveryOrder(
                id, deliveryCode, prescriptionCode, prescriptionId,
                patientId, patientName, patientPhone, deliveryAddress, destinationCity,
                pharmacyTenantId, pharmacyName, logisticsTenantId,
                agentId, agentName, DeliveryStatus.ASSIGNED,
                packageDescription, specialInstructions,
                otpHash, otpSalt, otpExpiresAt, otpAttemptsRemaining, otpClear,
                createdAt, when, pickedUpAt, deliveredAt, recipientConfirmation, failureReason
        );
    }

    public DeliveryOrder withPickup(Instant when) {
        return new DeliveryOrder(
                id, deliveryCode, prescriptionCode, prescriptionId,
                patientId, patientName, patientPhone, deliveryAddress, destinationCity,
                pharmacyTenantId, pharmacyName, logisticsTenantId,
                deliveryAgentId, deliveryAgentName, DeliveryStatus.PICKED_UP,
                packageDescription, specialInstructions,
                otpHash, otpSalt, otpExpiresAt, otpAttemptsRemaining, otpClear,
                createdAt, assignedAt, when, deliveredAt, recipientConfirmation, failureReason
        );
    }

    public DeliveryOrder withInTransit() {
        return new DeliveryOrder(
                id, deliveryCode, prescriptionCode, prescriptionId,
                patientId, patientName, patientPhone, deliveryAddress, destinationCity,
                pharmacyTenantId, pharmacyName, logisticsTenantId,
                deliveryAgentId, deliveryAgentName, DeliveryStatus.IN_TRANSIT,
                packageDescription, specialInstructions,
                otpHash, otpSalt, otpExpiresAt, otpAttemptsRemaining, otpClear,
                createdAt, assignedAt, pickedUpAt, deliveredAt, recipientConfirmation, failureReason
        );
    }

    public DeliveryOrder withDeliverySuccess(Instant when, String recipientSignature) {
        return new DeliveryOrder(
                id, deliveryCode, prescriptionCode, prescriptionId,
                patientId, patientName, patientPhone, deliveryAddress, destinationCity,
                pharmacyTenantId, pharmacyName, logisticsTenantId,
                deliveryAgentId, deliveryAgentName, DeliveryStatus.DELIVERED,
                packageDescription, specialInstructions,
                otpHash, otpSalt, otpExpiresAt, 0, null, // Effacement du code clair après livraison
                createdAt, assignedAt, pickedUpAt, when, recipientSignature, null
        );
    }

    public DeliveryOrder withDecrementedAttempts() {
        int remaining = Math.max(0, otpAttemptsRemaining - 1);
        DeliveryStatus nextStatus = (remaining == 0) ? DeliveryStatus.FAILED : status;
        String reason = (remaining == 0) ? "Nombre maximal de tentatives OTP dépassé (sécurité anti-bruteforce)." : failureReason;
        return new DeliveryOrder(
                id, deliveryCode, prescriptionCode, prescriptionId,
                patientId, patientName, patientPhone, deliveryAddress, destinationCity,
                pharmacyTenantId, pharmacyName, logisticsTenantId,
                deliveryAgentId, deliveryAgentName, nextStatus,
                packageDescription, specialInstructions,
                otpHash, otpSalt, otpExpiresAt, remaining, (remaining == 0 ? null : otpClear),
                createdAt, assignedAt, pickedUpAt, deliveredAt, recipientConfirmation, reason
        );
    }

    public DeliveryOrder withFailure(String reason) {
        return new DeliveryOrder(
                id, deliveryCode, prescriptionCode, prescriptionId,
                patientId, patientName, patientPhone, deliveryAddress, destinationCity,
                pharmacyTenantId, pharmacyName, logisticsTenantId,
                deliveryAgentId, deliveryAgentName, DeliveryStatus.FAILED,
                packageDescription, specialInstructions,
                otpHash, otpSalt, otpExpiresAt, 0, null,
                createdAt, assignedAt, pickedUpAt, deliveredAt, recipientConfirmation, reason
        );
    }

    public DeliveryOrder withCancellation(String reason) {
        return new DeliveryOrder(
                id, deliveryCode, prescriptionCode, prescriptionId,
                patientId, patientName, patientPhone, deliveryAddress, destinationCity,
                pharmacyTenantId, pharmacyName, logisticsTenantId,
                deliveryAgentId, deliveryAgentName, DeliveryStatus.CANCELLED,
                packageDescription, specialInstructions,
                otpHash, otpSalt, otpExpiresAt, 0, null,
                createdAt, assignedAt, pickedUpAt, deliveredAt, recipientConfirmation, reason
        );
    }
}
