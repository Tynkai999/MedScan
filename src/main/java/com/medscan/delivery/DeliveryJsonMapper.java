package com.medscan.delivery;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur JSON natif pour les commandes de livraison (MOD-09).
 * Garantit la protection stricte du code OTP selon les droits RBAC de l'acteur requérant.
 */
public final class DeliveryJsonMapper {

    private DeliveryJsonMapper() {}

    public static String toJson(DeliveryOrder d, boolean includeOtp) {
        if (d == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(d.id()).append("\",");
        sb.append("\"deliveryCode\":\"").append(JsonHelper.escape(d.deliveryCode())).append("\",");
        sb.append("\"prescriptionCode\":").append(d.prescriptionCode() != null ? "\"" + JsonHelper.escape(d.prescriptionCode()) + "\"" : "null").append(",");
        sb.append("\"prescriptionId\":").append(d.prescriptionId() != null ? "\"" + d.prescriptionId() + "\"" : "null").append(",");
        sb.append("\"patientId\":\"").append(d.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(d.patientName())).append("\",");
        sb.append("\"patientPhone\":\"").append(JsonHelper.escape(d.patientPhone())).append("\",");
        sb.append("\"deliveryAddress\":\"").append(JsonHelper.escape(d.deliveryAddress())).append("\",");
        sb.append("\"destinationCity\":\"").append(JsonHelper.escape(d.destinationCity())).append("\",");
        sb.append("\"pharmacyTenantId\":\"").append(d.pharmacyTenantId()).append("\",");
        sb.append("\"pharmacyName\":\"").append(JsonHelper.escape(d.pharmacyName())).append("\",");
        sb.append("\"logisticsTenantId\":").append(d.logisticsTenantId() != null ? "\"" + d.logisticsTenantId() + "\"" : "null").append(",");
        sb.append("\"deliveryAgentId\":").append(d.deliveryAgentId() != null ? "\"" + d.deliveryAgentId() + "\"" : "null").append(",");
        sb.append("\"deliveryAgentName\":").append(d.deliveryAgentName() != null ? "\"" + JsonHelper.escape(d.deliveryAgentName()) + "\"" : "null").append(",");
        sb.append("\"status\":\"").append(d.status().name()).append("\",");
        sb.append("\"packageDescription\":\"").append(JsonHelper.escape(d.packageDescription())).append("\",");
        sb.append("\"specialInstructions\":").append(d.specialInstructions() != null ? "\"" + JsonHelper.escape(d.specialInstructions()) + "\"" : "null").append(",");

        // Sécurité OTP : Seul le patient légitime ou la consultation dédiée reçoit le code clair !
        if (includeOtp && d.otpClear() != null && d.status() != DeliveryStatus.DELIVERED) {
            sb.append("\"otpCode\":\"").append(JsonHelper.escape(d.otpClear())).append("\",");
        } else {
            sb.append("\"otpRequired\":true,");
        }
        sb.append("\"otpAttemptsRemaining\":").append(d.otpAttemptsRemaining()).append(",");
        sb.append("\"otpExpiresAt\":").append(d.otpExpiresAt() != null ? "\"" + d.otpExpiresAt() + "\"" : "null").append(",");

        sb.append("\"createdAt\":\"").append(d.createdAt()).append("\",");
        sb.append("\"assignedAt\":").append(d.assignedAt() != null ? "\"" + d.assignedAt() + "\"" : "null").append(",");
        sb.append("\"pickedUpAt\":").append(d.pickedUpAt() != null ? "\"" + d.pickedUpAt() + "\"" : "null").append(",");
        sb.append("\"deliveredAt\":").append(d.deliveredAt() != null ? "\"" + d.deliveredAt() + "\"" : "null").append(",");
        sb.append("\"recipientConfirmation\":").append(d.recipientConfirmation() != null ? "\"" + JsonHelper.escape(d.recipientConfirmation()) + "\"" : "null").append(",");
        sb.append("\"failureReason\":").append(d.failureReason() != null ? "\"" + JsonHelper.escape(d.failureReason()) + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }

    public static String toListJson(List<DeliveryOrder> list, boolean includeOtp) {
        if (list == null) return "[]";
        return "[" + list.stream().map(d -> toJson(d, includeOtp)).collect(Collectors.joining(",")) + "]";
    }
}
