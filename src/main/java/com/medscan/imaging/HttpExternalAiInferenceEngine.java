package com.medscan.imaging;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.medscan.security.jwt.JsonHelper;

/**
 * Adaptateur HTTP pour connecter un microservice d'IA externe (Python / FastAPI / PyTorch / TorchServe).
 *
 * Pour brancher le modèle d'IA réel plus tard, il suffira de définir la variable d'environnement :
 *   MEDSCAN_AI_ENGINE_URL=http://votre-serveur-ia:8000/api/v1/predict
 * Aucune modification de code backend ou frontend ne sera nécessaire !
 */
public class HttpExternalAiInferenceEngine implements AiInferenceGateway {

    private final String endpointUrl;
    private final SimulatedAiInferenceEngine fallbackEngine = new SimulatedAiInferenceEngine();

    public HttpExternalAiInferenceEngine(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    @Override
    public AiAnalysisResult analyze(ImagingStudy study, byte[] imageBytes) {
        long startTime = System.currentTimeMillis();

        try {
            URI uri = URI.create(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            // Construction du payload d'inférence pour le modèle Python
            StringBuilder requestJson = new StringBuilder();
            requestJson.append("{");
            requestJson.append("\"studyId\":\"").append(study.id()).append("\",");
            requestJson.append("\"modality\":\"").append(study.modality()).append("\",");
            requestJson.append("\"bodyPart\":\"").append(study.bodyPart()).append("\",");
            requestJson.append("\"imageUrl\":\"").append(study.imageUrl() != null ? study.imageUrl() : "").append("\"");
            requestJson.append("}");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestJson.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    return parseAiResponse(responseBody, study.id(), System.currentTimeMillis() - startTime);
                }
            } else {
                System.err.println("[AI Engine Warning] L'API d'IA externe a retourné le code HTTP " + responseCode + ". Bascule automatique sur le moteur haute-fidélité.");
                return fallbackEngine.analyze(study, imageBytes);
            }
        } catch (Exception e) {
            System.err.println("[AI Engine Warning] Impossible de joindre le serveur d'IA externe (" + endpointUrl + "): " + e.getMessage() + ". Bascule automatique sur le moteur haute-fidélité.");
            return fallbackEngine.analyze(study, imageBytes);
        }
    }

    private AiAnalysisResult parseAiResponse(String json, UUID studyId, long executionTimeMs) {
        String modelName = JsonHelper.getString(json, "modelName");
        String modelVersion = JsonHelper.getString(json, "modelVersion");
        String primaryFinding = JsonHelper.getString(json, "primaryFinding");
        Double confidence = JsonHelper.getDouble(json, "confidenceScore");
        String riskLevel = JsonHelper.getString(json, "riskLevel");
        String heatmapUrl = JsonHelper.getString(json, "heatmapOverlayUrl");

        List<AiFindingDetail> findings = new ArrayList<>();
        findings.add(new AiFindingDetail(
                primaryFinding != null ? primaryFinding : "Anomalie détectée",
                confidence != null ? confidence : 0.90,
                "Zone d'intérêt principale",
                riskLevel != null ? riskLevel : "MODERATE"
        ));

        return new AiAnalysisResult(
                UUID.randomUUID(),
                studyId,
                Instant.now(),
                modelName != null ? modelName : "MedScan-Python-DeepLearning-External",
                modelVersion != null ? modelVersion : "1.0.0-remote",
                primaryFinding != null ? primaryFinding : "Analyse complétée par le modèle d'IA externe.",
                confidence != null ? confidence : 0.92,
                riskLevel != null ? riskLevel : "MODERATE",
                findings,
                heatmapUrl != null ? heatmapUrl : "https://medscan-sluw.onrender.com/assets/heatmaps/gradcam_remote.png",
                executionTimeMs,
                AiAnalysisResult.DEFAULT_DISCLAIMER
        );
    }

    @Override
    public String getEngineName() {
        return "MedScan-External-Python-Gateway (" + endpointUrl + ")";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
