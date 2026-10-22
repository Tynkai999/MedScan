package com.medscan.sync;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Réponse du serveur suite au traitement d'un lot d'opérations hors-ligne (MOD-10).
 * Fournit un rapport d'exécution détaillé pour chaque opération (idempotence, application, rejet)
 * ainsi que le delta différentiel mis à jour pour achever la réconciliation bidirectionnelle.
 */
public record SyncBatchResponse(
        UUID batchId,
        Instant processedAt,
        int totalOperations,
        int appliedCount,
        int duplicateCount,
        int errorCount,
        List<SyncOperationResult> results,
        SyncPackage delta
) {
    public SyncBatchResponse {
        if (processedAt == null) processedAt = Instant.now();
        if (results == null) results = Collections.emptyList();
    }
}
