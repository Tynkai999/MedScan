package com.medscan.sync;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Requête de synchronisation de lot d'opérations hors-ligne (MOD-10).
 * Reçue lors du rétablissement de la connectivité réseau depuis un terminal mobile ou poste de terrain.
 */
public record SyncBatchRequest(
        UUID batchId,
        String deviceId,
        Instant clientTimestamp,
        List<SyncOperation> operations,
        Instant pullSince
) {
    public SyncBatchRequest {
        if (batchId == null) batchId = UUID.randomUUID();
        if (deviceId == null || deviceId.isBlank()) deviceId = "UNKNOWN_DEVICE";
        if (clientTimestamp == null) clientTimestamp = Instant.now();
        if (operations == null) operations = Collections.emptyList();
    }
}
