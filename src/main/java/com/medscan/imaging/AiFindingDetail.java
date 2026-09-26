package com.medscan.imaging;

/**
 * Détail d'une anomalie détectée par le modèle d'IA avec probabilité et région anatomique.
 */
public record AiFindingDetail(
        String label,
        double probability,
        String anatomicalRegion,
        String severity // NORMAL, LOW, MODERATE, HIGH, CRITICAL
) {
}
