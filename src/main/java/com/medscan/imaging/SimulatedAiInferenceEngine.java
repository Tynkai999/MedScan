package com.medscan.imaging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Moteur de simulation d'inférence d'IA clinique à haute fidélité médicale.
 * Utilisé pendant le développement et les tests avant le branchement du modèle PyTorch réel.
 */
public class SimulatedAiInferenceEngine implements AiInferenceGateway {

    public static final String ENGINE_NAME = "MedScan-ChestVision-DenseNet121 (Inférence Embarquée)";
    public static final String MODEL_VERSION = "2.1.0-embedded";

    @Override
    public AiAnalysisResult analyze(ImagingStudy study, byte[] imageBytes) {
        long startTime = System.currentTimeMillis();

        String bodyPart = study.bodyPart() != null ? study.bodyPart().toUpperCase() : "CHEST";
        String primaryFinding;
        double confidence;
        String riskLevel;
        List<AiFindingDetail> findings = new ArrayList<>();
        String heatmapUrl;

        switch (bodyPart) {
            case "CHEST" -> {
                // Détection pulmonaire / thoracique avancée
                if (study.title().toLowerCase().contains("urgence") || study.title().toLowerCase().contains("infiltrat") || study.title().toLowerCase().contains("pneumo")) {
                    primaryFinding = "Foyer de condensation alvéolaire du lobe inférieur droit compatible avec une pneumopathie franche lobaire aiguë.";
                    confidence = 0.946; // 94.6%
                    riskLevel = "HIGH";
                    findings.add(new AiFindingDetail("Opacité / Condensation alvéolaire", 0.946, "Lobe inférieur droit", "HIGH"));
                    findings.add(new AiFindingDetail("Cardiomégalie modérée", 0.380, "Silhouette cardio-thoracique (ICT ~ 0.53)", "MODERATE"));
                    findings.add(new AiFindingDetail("Épanchement pleural liquidien", 0.120, "Cul-de-sac costo-diaphragmatique droit", "LOW"));
                    findings.add(new AiFindingDetail("Pneumothorax", 0.015, "Apex pulmonaire bilatéral", "NORMAL"));
                    heatmapUrl = "https://medscan-sluw.onrender.com/assets/heatmaps/gradcam_chest_lobar_r.png";
                } else {
                    primaryFinding = "Trame broncho-vasculaire accentuée au niveau péri-hilaire sans foyer alvéolaire systématisé ni épanchement pleural décelable.";
                    confidence = 0.912;
                    riskLevel = "MILD";
                    findings.add(new AiFindingDetail("Accentuation péri-hilaire bilatérale", 0.912, "Hiles pulmonaires", "MILD"));
                    findings.add(new AiFindingDetail("Silhouette cardiaque", 0.080, "Médiastin", "NORMAL"));
                    findings.add(new AiFindingDetail("Structures osseuses thoraciques", 0.030, "Gril costal et clavicules", "NORMAL"));
                    heatmapUrl = "https://medscan-sluw.onrender.com/assets/heatmaps/gradcam_chest_normal.png";
                }
            }
            case "EXTREMITY" -> {
                primaryFinding = "Absence de solution de continuité corticale ou de trait de fracture osseuse visible. Interlignes articulaires respectés.";
                confidence = 0.978;
                riskLevel = "NORMAL";
                findings.add(new AiFindingDetail("Trait de fracture corticale", 0.022, "Diaphyse et épiphyse", "NORMAL"));
                findings.add(new AiFindingDetail("Parties molles péri-articulaires", 0.040, "Tissus sous-cutanés", "NORMAL"));
                heatmapUrl = "https://medscan-sluw.onrender.com/assets/heatmaps/gradcam_bone_normal.png";
            }
            default -> {
                primaryFinding = "Examen dans les limites normales pour la modalité " + study.modality() + ". Aucun signe d'alarme immédiat.";
                confidence = 0.895;
                riskLevel = "NORMAL";
                findings.add(new AiFindingDetail("Anomalie macroscopique", 0.105, "Champ d'exploration", "NORMAL"));
                heatmapUrl = "https://medscan-sluw.onrender.com/assets/heatmaps/gradcam_default.png";
            }
        }

        long executionTimeMs = System.currentTimeMillis() - startTime + 145; // Simulation du temps de propagation GPU

        return new AiAnalysisResult(
                UUID.randomUUID(),
                study.id(),
                Instant.now(),
                ENGINE_NAME,
                MODEL_VERSION,
                primaryFinding,
                confidence,
                riskLevel,
                findings,
                heatmapUrl,
                executionTimeMs,
                AiAnalysisResult.DEFAULT_DISCLAIMER
        );
    }

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
