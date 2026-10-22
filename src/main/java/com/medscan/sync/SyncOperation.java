package com.medscan.sync;

import java.time.Instant;
import java.util.UUID;

/**
 * Opération clinique atomique effectuée hors-ligne (MOD-10).
 * Identifiée par un identifiant unique (operationId) généré par le client mobile
 * pour garantir l'idempotence stricte et éliminer tout risque de doublon en cas de reconnexion.
 */
public record SyncOperation(
        UUID operationId,
        String entityType,    // PATIENT, VITAL_SIGNS, CONSULTATION
        String action,        // CREATE, UPDATE
        Instant occurredAt,
        String payloadJson
) {
    public SyncOperation {
        if (operationId == null) {
            operationId = UUID.randomUUID();
        }
        if (entityType == null || entityType.isBlank()) {
            entityType = "UNKNOWN";
        } else {
            entityType = entityType.toUpperCase().trim();
        }
        if (action == null || action.isBlank()) {
            action = "CREATE";
        } else {
            action = action.toUpperCase().trim();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (payloadJson == null) {
            payloadJson = "{}";
        }
    }
}
