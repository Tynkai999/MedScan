package com.medscan.lab;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Prescription et compte-rendu d'analyse biologique médicale (MOD-13).
 * Traçabilité complète du prescripteur (DOCTOR) et du valideur technique (LAB_TECHNICIAN).
 */
public record LabOrder(
        UUID id,
        String orderCode,           // ex: LAB-2026-0042
        UUID tenantId,
        UUID patientId,
        String patientName,
        UUID prescriberId,
        String prescriberName,
        UUID technicianId,
        String technicianName,
        String testCategory,        // HEMATOLOGY, PARASITOLOGY, BIOCHEMISTRY, IMMUNOLOGY, URINARY
        String testName,            // Hémogramme complet, Recherche Paludisme (GE/TDR), Glycémie à jeun
        String priority,            // ROUTINE, URGENT, STAT
        String status,              // ORDERED, SAMPLE_COLLECTED, IN_ANALYSIS, VALIDATED, CANCELLED
        String clinicalIndication,
        Instant sampleCollectedAt,
        Instant validatedAt,
        List<LabResultItem> results,
        boolean hasCriticalAlert,
        Instant createdAt
) {
    public LabOrder withSampleCollected(Instant collectedAt) {
        return new LabOrder(
                id, orderCode, tenantId, patientId, patientName, prescriberId, prescriberName,
                technicianId, technicianName, testCategory, testName, priority,
                "SAMPLE_COLLECTED", clinicalIndication, collectedAt, validatedAt,
                results, hasCriticalAlert, createdAt
        );
    }

    public LabOrder withResultsValidated(UUID techId, String techName, List<LabResultItem> validatedResults, Instant validatedTime) {
        boolean critical = validatedResults != null && validatedResults.stream().anyMatch(LabResultItem::isCritical);
        return new LabOrder(
                id, orderCode, tenantId, patientId, patientName, prescriberId, prescriberName,
                techId, techName, testCategory, testName, priority,
                "VALIDATED", clinicalIndication, sampleCollectedAt, validatedTime,
                validatedResults != null ? validatedResults : Collections.emptyList(), critical, createdAt
        );
    }
}
