package com.medscan.imaging;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.medscan.clinical.ClinicalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagingServiceTest {

    private ImagingService imagingService;
    private ClinicalService clinicalService;
    private final UUID tenantHospital = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private final UUID doctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
    private final UUID radiologistId = UUID.fromString("acd191a6-4846-45d7-bd38-f0a042b59348");
    private final UUID patientFatouId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        imagingService = new ImagingService(clinicalService, new SimulatedAiInferenceEngine());
    }

    @Test
    @DisplayName("Recherche et création d'études d'imagerie médicale")
    void testCreateAndSearchImagingStudies() {
        List<ImagingStudy> studies = imagingService.searchStudies(tenantHospital, patientFatouId, Set.of("RADIOLOGIST"));
        assertNotNull(studies);
        assertTrue(studies.size() >= 1);
        assertEquals("XR", studies.get(0).modality());
        assertEquals("CHEST", studies.get(0).bodyPart());

        // Création d'une nouvelle étude
        ImagingStudy newStudy = new ImagingStudy(
                UUID.randomUUID(),
                patientFatouId,
                "Fatou Ouedraogo",
                "US",
                "ABDOMEN",
                "Échographie Abdominale Complète",
                "https://medscan-sluw.onrender.com/assets/imaging/echo_fatou_01.png",
                Instant.now(),
                tenantHospital,
                doctorId,
                "Dr. Seydou Traore",
                "ACQUIRED",
                null,
                null
        );

        ImagingStudy created = imagingService.createStudy(newStudy, radiologistId, "Dr. Aïssatou Sawadogo", "RADIOLOGIST", tenantHospital);
        assertNotNull(created);
        assertEquals("ACQUIRED", created.status());
    }

    @Test
    @DisplayName("Inférence du modèle d'IA clinique : score de confiance et explicabilité Grad-CAM")
    void testTriggerAiInference() {
        UUID studyId = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        ImagingStudy analyzed = imagingService.triggerAiAnalysis(studyId, doctorId, "Dr. Seydou Traore", "DOCTOR", tenantHospital);

        assertNotNull(analyzed.aiAnalysis());
        AiAnalysisResult ai = analyzed.aiAnalysis();
        assertTrue(ai.confidenceScore() > 0.80, "Le score de confiance doit être supérieur à 80%");
        assertNotNull(ai.primaryFinding());
        assertNotNull(ai.heatmapOverlayUrl(), "Une URL de Heatmap Grad-CAM doit être fournie");
        assertNotNull(ai.disclaimer(), "La mention de responsabilité médicale légale doit être présente");
        assertTrue(ai.findings().size() > 0);
    }

    @Test
    @DisplayName("Validation du compte-rendu radiologique par un praticien (Humain dans la boucle)")
    void testRadiologistValidationReport() {
        UUID studyId = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        RadiologistReport report = new RadiologistReport(
                UUID.randomUUID(),
                studyId,
                radiologistId,
                "Dr. Aïssatou Sawadogo",
                "RADIOLOGIST",
                Instant.now(),
                "Confirmation du diagnostic d'IA. Absence de lésion pleuro-parenchymateuse évolutive.",
                "AGREED",
                "Surveillance clinique de routine."
        );

        ImagingStudy validated = imagingService.addRadiologistReport(studyId, report, radiologistId, "Dr. Aïssatou Sawadogo", "RADIOLOGIST", tenantHospital);
        assertEquals("VALIDATED_BY_DOCTOR", validated.status());
        assertNotNull(validated.report());
        assertEquals("AGREED", validated.report().aiAgreementStatus());
    }

    @Test
    @DisplayName("L'adaptateur externe HTTP bascule automatiquement sans crasher si le serveur Python n'est pas encore démarré")
    void testExternalAiGatewayFallback() {
        HttpExternalAiInferenceEngine externalEngine = new HttpExternalAiInferenceEngine("http://127.0.0.1:59999/predict");
        ImagingStudy study = imagingService.findStudyById(UUID.fromString("c0000000-0000-0000-0000-000000000001")).orElseThrow();

        // Doit basculer sur le moteur haute-fidélité sans lever d'exception bloquante
        AiAnalysisResult result = externalEngine.analyze(study, null);
        assertNotNull(result);
        assertTrue(result.confidenceScore() > 0.5);
    }
}
