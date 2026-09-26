package com.medscan.imaging;

/**
 * Fabrique dynamique du moteur d'IA clinique.
 * Sélectionne automatiquement le connecteur externe (Python/FastAPI) si configuré,
 * ou le moteur de simulation haute fidélité embarqué.
 */
public final class AiGatewayFactory {

    private AiGatewayFactory() {
    }

    public static AiInferenceGateway createEngine() {
        String externalUrl = System.getProperty("medscan.ai.engine.url", System.getenv("MEDSCAN_AI_ENGINE_URL"));
        if (externalUrl != null && !externalUrl.isBlank()) {
            System.out.println(">>> [AI Gateway] Configuration détectée : Connexion au microservice IA Python sur: " + externalUrl);
            return new HttpExternalAiInferenceEngine(externalUrl.trim());
        }

        System.out.println(">>> [AI Gateway] Utilisation du moteur d'inférence clinique haute-fidélité embarqué.");
        return new SimulatedAiInferenceEngine();
    }
}
