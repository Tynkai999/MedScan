package com.medscan.imaging;

/**
 * Interface découplée du moteur d'inférence d'IA clinique (Port & Adapter Pattern - MOD-06).
 *
 * Permet au système de fonctionner immédiatement avec le moteur de simulation clinique,
 * et d'intégrer ultérieurement un service Python / FastAPI / PyTorch / MONAI sans modifier
 * aucune ligne du code métier ni du frontend.
 */
public interface AiInferenceGateway {

    /**
     * Analyse un examen d'imagerie médicale et produit un résultat diagnostique prédictif.
     *
     * @param study L'examen radiologique avec ses métadonnées cliniques
     * @param imageBytes Les octets binaires du cliché radiologique (optionnels si stockés par URL)
     * @return Le résultat d'inférence avec score de confiance et explicabilité Grad-CAM
     */
    AiAnalysisResult analyze(ImagingStudy study, byte[] imageBytes);

    /**
     * Retourne le nom du modèle ou du moteur actif.
     */
    String getEngineName();

    /**
     * Indique si le moteur d'IA est opérationnel et prêt à inférer.
     */
    boolean isAvailable();
}
