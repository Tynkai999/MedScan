package com.medscan.imaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur et désérialiseur JSON pour les examens d'imagerie et les résultats d'IA.
 */
public final class ImagingJsonMapper {

    private ImagingJsonMapper() {
    }

    public static String toJson(AiFindingDetail finding) {
        if (finding == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"label\":\"").append(JsonHelper.escape(finding.label())).append("\",");
        sb.append("\"probability\":").append(finding.probability()).append(",");
        sb.append("\"anatomicalRegion\":\"").append(JsonHelper.escape(finding.anatomicalRegion())).append("\",");
        sb.append("\"severity\":\"").append(JsonHelper.escape(finding.severity())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toJson(AiAnalysisResult ai) {
        if (ai == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(ai.id()).append("\",");
        sb.append("\"studyId\":\"").append(ai.studyId()).append("\",");
        sb.append("\"analyzedAt\":\"").append(ai.analyzedAt()).append("\",");
        sb.append("\"modelName\":\"").append(JsonHelper.escape(ai.modelName())).append("\",");
        sb.append("\"modelVersion\":\"").append(JsonHelper.escape(ai.modelVersion())).append("\",");
        sb.append("\"primaryFinding\":\"").append(JsonHelper.escape(ai.primaryFinding())).append("\",");
        sb.append("\"confidenceScore\":").append(ai.confidenceScore()).append(",");
        sb.append("\"riskLevel\":\"").append(JsonHelper.escape(ai.riskLevel())).append("\",");
        sb.append("\"findings\":[")
                .append(ai.findings().stream().map(ImagingJsonMapper::toJson).collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"heatmapOverlayUrl\":\"").append(JsonHelper.escape(ai.heatmapOverlayUrl())).append("\",");
        sb.append("\"executionTimeMs\":").append(ai.executionTimeMs()).append(",");
        sb.append("\"disclaimer\":\"").append(JsonHelper.escape(ai.disclaimer())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toJson(RadiologistReport report) {
        if (report == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(report.id()).append("\",");
        sb.append("\"studyId\":\"").append(report.studyId()).append("\",");
        sb.append("\"doctorId\":\"").append(report.doctorId()).append("\",");
        sb.append("\"doctorName\":\"").append(JsonHelper.escape(report.doctorName())).append("\",");
        sb.append("\"doctorRole\":\"").append(JsonHelper.escape(report.doctorRole())).append("\",");
        sb.append("\"validatedAt\":\"").append(report.validatedAt()).append("\",");
        sb.append("\"conclusion\":\"").append(JsonHelper.escape(report.conclusion())).append("\",");
        sb.append("\"aiAgreementStatus\":\"").append(JsonHelper.escape(report.aiAgreementStatus())).append("\",");
        sb.append("\"recommendedActions\":\"").append(JsonHelper.escape(report.recommendedActions())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toJson(ImagingStudy study) {
        if (study == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(study.id()).append("\",");
        sb.append("\"patientId\":\"").append(study.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(study.patientName())).append("\",");
        sb.append("\"modality\":\"").append(JsonHelper.escape(study.modality())).append("\",");
        sb.append("\"bodyPart\":\"").append(JsonHelper.escape(study.bodyPart())).append("\",");
        sb.append("\"title\":\"").append(JsonHelper.escape(study.title())).append("\",");
        sb.append("\"imageUrl\":\"").append(JsonHelper.escape(study.imageUrl())).append("\",");
        sb.append("\"studyDate\":\"").append(study.studyDate()).append("\",");
        sb.append("\"tenantId\":\"").append(study.tenantId()).append("\",");
        sb.append("\"referringDoctorId\":\"").append(study.referringDoctorId()).append("\",");
        sb.append("\"referringDoctorName\":\"").append(JsonHelper.escape(study.referringDoctorName())).append("\",");
        sb.append("\"status\":\"").append(JsonHelper.escape(study.status())).append("\",");
        sb.append("\"aiAnalysis\":").append(toJson(study.aiAnalysis())).append(",");
        sb.append("\"report\":").append(toJson(study.report()));
        sb.append("}");
        return sb.toString();
    }

    public static String toStudyListJson(List<ImagingStudy> list) {
        return "[" + list.stream().map(ImagingJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    public static ImagingStudy parseStudy(String json, UUID tenantId, UUID referringDoctorId, String referringDoctorName) {
        String patientIdStr = JsonHelper.getString(json, "patientId");
        UUID patientId = patientIdStr != null && !patientIdStr.isBlank()
                ? UUID.fromString(patientIdStr)
                : UUID.randomUUID();

        String patientName = JsonHelper.getString(json, "patientName");
        String modality = JsonHelper.getString(json, "modality");
        String bodyPart = JsonHelper.getString(json, "bodyPart");
        String title = JsonHelper.getString(json, "title");
        String imageUrl = JsonHelper.getString(json, "imageUrl");

        return new ImagingStudy(
                UUID.randomUUID(),
                patientId,
                patientName != null ? patientName : "Patient",
                modality != null ? modality : "XR",
                bodyPart != null ? bodyPart : "CHEST",
                title != null ? title : "Examen Radiologique",
                imageUrl != null ? imageUrl : "https://medscan-sluw.onrender.com/assets/imaging/default.png",
                Instant.now(),
                tenantId,
                referringDoctorId,
                referringDoctorName,
                "ACQUIRED",
                null,
                null
        );
    }

    public static RadiologistReport parseReport(String json, UUID studyId, UUID doctorId, String doctorName, String doctorRole) {
        String conclusion = JsonHelper.getString(json, "conclusion");
        String aiAgreement = JsonHelper.getString(json, "aiAgreementStatus");
        String recommendedActions = JsonHelper.getString(json, "recommendedActions");

        return new RadiologistReport(
                UUID.randomUUID(),
                studyId,
                doctorId,
                doctorName,
                doctorRole,
                Instant.now(),
                conclusion != null ? conclusion : "Examen examiné et validé.",
                aiAgreement != null ? aiAgreement : "AGREED",
                recommendedActions != null ? recommendedActions : ""
        );
    }
}
