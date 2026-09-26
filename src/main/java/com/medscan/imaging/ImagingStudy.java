package com.medscan.imaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Examen d'imagerie médicale (Radiographie, Scanner, Échographie - MOD-05).
 */
public record ImagingStudy(
        UUID id,
        UUID patientId,
        String patientName,
        String modality, // XR (Radio standard), CT (Scanner), US (Échographie)
        String bodyPart, // CHEST, ABDOMEN, PELVIS, EXTREMITY, SKULL
        String title,
        String imageUrl,
        Instant studyDate,
        UUID tenantId,
        UUID referringDoctorId,
        String referringDoctorName,
        String status, // ACQUIRED, ANALYZED_AI, VALIDATED_BY_DOCTOR
        AiAnalysisResult aiAnalysis,
        RadiologistReport report
) {
    public ImagingStudy withAiAnalysis(AiAnalysisResult analysis) {
        return new ImagingStudy(
                id, patientId, patientName, modality, bodyPart, title, imageUrl,
                studyDate, tenantId, referringDoctorId, referringDoctorName,
                "ANALYZED_AI", analysis, report
        );
    }

    public ImagingStudy withReport(RadiologistReport newReport) {
        return new ImagingStudy(
                id, patientId, patientName, modality, bodyPart, title, imageUrl,
                studyDate, tenantId, referringDoctorId, referringDoctorName,
                "VALIDATED_BY_DOCTOR", aiAnalysis, newReport
        );
    }
}
