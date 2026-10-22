package com.medscan.sync;

import java.util.UUID;

/**
 * Résultat d'exécution d'une opération de synchronisation (MOD-10).
 * Indique si l'opération a été nouvellement appliquée, ignorée pour cause de doublon (idempotence),
 * rejetée en cas d'erreur de validation, ou réconciliée suite à un conflit.
 */
public record SyncOperationResult(
        UUID operationId,
        String status,       // APPLIED, DUPLICATE_IGNORED, REJECTED, CONFLICT_RESOLVED
        String entityType,
        UUID entityId,
        String message
) {
    public static SyncOperationResult applied(UUID opId, String entityType, UUID entityId, String msg) {
        return new SyncOperationResult(opId, "APPLIED", entityType, entityId, msg);
    }

    public static SyncOperationResult duplicate(UUID opId, String entityType, UUID entityId, String msg) {
        return new SyncOperationResult(opId, "DUPLICATE_IGNORED", entityType, entityId, msg);
    }

    public static SyncOperationResult rejected(UUID opId, String entityType, String msg) {
        return new SyncOperationResult(opId, "REJECTED", entityType, null, msg);
    }

    public static SyncOperationResult conflictResolved(UUID opId, String entityType, UUID entityId, String msg) {
        return new SyncOperationResult(opId, "CONFLICT_RESOLVED", entityType, entityId, msg);
    }
}
