package com.medscan.clinical;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrée de journal d'audit append-only (MOD-11).
 * Traçabilité exhaustive des accès aux données de santé et actions critiques.
 */
public record AuditEvent(
        UUID id,
        Instant timestamp,
        UUID actorId,
        String actorUsername,
        String actorRole,
        UUID tenantId,
        String action,
        String resource,
        String resourceId,
        String status,
        String details
) {
}
