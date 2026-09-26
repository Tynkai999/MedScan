package com.medscan.imaging;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;

/**
 * Service de gestion des examens d'imagerie médicale (MOD-05) et d'orchestration IA (MOD-06).
 */
public class ImagingService {

    private static final UUID PLATFORM_TENANT = UUID.fromString("b47c7913-35d0-43e3-8ec1-5614dec9ffcd");

    private final Map<UUID, ImagingStudy> studiesById = new ConcurrentHashMap<>();
    private final AiInferenceGateway aiEngine;
    private final ClinicalService clinicalService;

    public ImagingService(ClinicalService clinicalService) {
        this(clinicalService, AiGatewayFactory.createEngine());
    }

    public ImagingService(ClinicalService clinicalService, AiInferenceGateway aiEngine) {
        this.clinicalService = clinicalService;
        this.aiEngine = aiEngine;
        initSeedStudies();
    }

    public List<ImagingStudy> searchStudies(UUID tenantId, UUID patientId, Set<String> roles) {
        boolean canSeeAll = roles.contains("SUPER_ADMIN") || roles.contains("AUDITOR") || PLATFORM_TENANT.equals(tenantId);

        return studiesById.values().stream()
                .filter(s -> canSeeAll || s.tenantId().equals(tenantId))
                .filter(s -> patientId == null || s.patientId().equals(patientId))
                .sorted((a, b) -> b.studyDate().compareTo(a.studyDate()))
                .collect(Collectors.toList());
    }

    public Optional<ImagingStudy> findStudyById(UUID studyId) {
        return Optional.ofNullable(studiesById.get(studyId));
    }

    public ImagingStudy createStudy(ImagingStudy study, UUID actorId, String actorName, String actorRole, UUID tenantId) {
        studiesById.put(study.id(), study);
        clinicalService.logAudit(actorId, actorName, actorRole, tenantId,
                "IMAGING_STUDY_CREATED", "ImagingStudy", study.id().toString(), "SUCCESS",
                "Nouvel examen d'imagerie radiologique enregistré : " + study.title() + " (" + study.modality() + ")");
        return study;
    }

    public ImagingStudy triggerAiAnalysis(UUID studyId, UUID actorId, String actorName, String actorRole, UUID tenantId) {
        ImagingStudy current = findStudyById(studyId)
                .orElseThrow(() -> new IllegalArgumentException("Examen radiologique introuvable: " + studyId));

        // Inférence d'IA via la passerelle découplée
        AiAnalysisResult result = aiEngine.analyze(current, null);
        ImagingStudy updated = current.withAiAnalysis(result);
        studiesById.put(studyId, updated);

        clinicalService.logAudit(actorId, actorName, actorRole, tenantId,
                "AI_ANALYSIS_EXECUTED", "ImagingStudy", studyId.toString(), "SUCCESS",
                "Inférence IA complétée par " + result.modelName() + " (Score de confiance: " + Math.round(result.confidenceScore() * 1000.0) / 10.0 + "%)");

        return updated;
    }

    public ImagingStudy addRadiologistReport(UUID studyId, RadiologistReport report, UUID actorId, String actorName, String actorRole, UUID tenantId) {
        ImagingStudy current = findStudyById(studyId)
                .orElseThrow(() -> new IllegalArgumentException("Examen radiologique introuvable: " + studyId));

        ImagingStudy updated = current.withReport(report);
        studiesById.put(studyId, updated);

        clinicalService.logAudit(actorId, actorName, actorRole, tenantId,
                "IMAGING_REPORT_VALIDATED", "ImagingStudy", studyId.toString(), "SUCCESS",
                "Compte-rendu médical signé par " + report.doctorName() + " (Avis sur IA: " + report.aiAgreementStatus() + ")");

        return updated;
    }

    public AiInferenceGateway getAiEngine() {
        return aiEngine;
    }

    private void initSeedStudies() {
        UUID tenantHospital = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
        UUID doctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
        UUID radiologistId = UUID.fromString("acd191a6-4846-45d7-bd38-f0a042b59348");
        UUID patientFatouId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");

        // Étude 1 : Radiographie Pulmonaire de Fatou Ouedraogo
        UUID s1Id = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        ImagingStudy s1 = new ImagingStudy(
                s1Id,
                patientFatouId,
                "Fatou Ouedraogo",
                "XR",
                "CHEST",
                "Radiographie Thorax Face et Profil",
                "https://medscan-sluw.onrender.com/assets/imaging/rx_chest_fatou_01.png",
                Instant.now().minusSeconds(86400 * 3),
                tenantHospital,
                doctorId,
                "Dr. Seydou Traore",
                "ACQUIRED",
                null,
                null
        );

        // Analyse IA pré-calculée pour la démo
        AiAnalysisResult ai1 = aiEngine.analyze(s1, null);
        s1 = s1.withAiAnalysis(ai1);

        // Validation par le radiologue
        RadiologistReport r1 = new RadiologistReport(
                UUID.randomUUID(),
                s1Id,
                radiologistId,
                "Dr. Aïssatou Sawadogo",
                "RADIOLOGIST",
                Instant.now().minusSeconds(86400 * 2),
                "Confirmation du diagnostic d'IA. Absence de foyer alvéolaire pneumonique constitué. Aspect compatible avec l'asthme connu de la patiente.",
                "AGREED",
                "Poursuivre bronchodilatateurs. Contrôle à 3 mois si persistance de la toux."
        );
        s1 = s1.withReport(r1);
        studiesById.put(s1Id, s1);
    }
}
