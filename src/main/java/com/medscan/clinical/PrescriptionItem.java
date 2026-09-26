package com.medscan.clinical;

import java.util.UUID;

/**
 * Ligne de prescription médicamenteuse (MOD-08).
 */
public record PrescriptionItem(
        UUID id,
        String medicationName,
        String dosage,
        String frequency,
        int durationDays,
        String instructions
) {
}
