package com.medscan.imaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Résultat d'inférence de vision par ordinateur du moteur d'IA clinique (MOD-06).
 * Inclut le diagnostic probabiliste, le score de confiance, et l'explicabilité (Grad-CAM heatmap).
 */
public record AiAnalysisResult(
        UUID id,
        UUID studyId,
        Instant analyzedAt,
        String modelName,
        String modelVersion,
        String primaryFinding,
        double confidenceScore,
        String riskLevel, // NORMAL, LOW, MODERATE, HIGH, CRITICAL
        List<AiFindingDetail> findings,
        String heatmapOverlayUrl,
        long executionTimeMs,
        String disclaimer
) {
    public static final String DEFAULT_DISCLAIMER =
            "Résultat d'aide au diagnostic clinique généré par intelligence artificielle à titre consultatif. " +
            "Conformément à la réglementation de santé, ce rapport ne se substitue pas à l'avis d'un médecin assermenté " +
            "et nécessite une validation médicale avant toute prise de décision thérapeutique.";
}
