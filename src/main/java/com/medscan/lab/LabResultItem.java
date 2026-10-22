package com.medscan.lab;

import java.util.UUID;

/**
 * Ligne de résultat d'analyse biologique (MOD-13).
 * Codifiée avec LOINC, unités UCUM, intervalles de normalité et indicateurs d'anomalie critique.
 */
public record LabResultItem(
        UUID id,
        String paramCode,       // ex: HEMOGLOBIN, PLASMODIUM_FALCIPARUM, GLUCOSE, CREATININE, PLATELETS
        String paramName,       // ex: Hémoglobine, Recherche Plasmodium (Goutte épaisse), Glycémie à jeun
        String loincCode,       // ex: 718-7, 50311-0, 2339-0, 2160-0, 777-3
        Double numericValue,
        String textValue,
        String unit,            // ex: g/dL, trophozoïtes/µL, g/L, mg/L, /mm3
        Double refIntervalLow,
        Double refIntervalHigh,
        boolean isAbnormal,
        boolean isCritical,
        String interpretation
) {
    public static LabResultItem ofNumeric(String paramCode, String paramName, String loincCode,
                                         Double val, String unit, Double low, Double high,
                                         Double criticalLow, Double criticalHigh, String interpretation) {
        boolean abnormal = (val != null) && ((low != null && val < low) || (high != null && val > high));
        boolean critical = (val != null) && ((criticalLow != null && val <= criticalLow) || (criticalHigh != null && val >= criticalHigh));
        return new LabResultItem(
                UUID.randomUUID(),
                paramCode,
                paramName,
                loincCode,
                val,
                val != null ? String.valueOf(val) : null,
                unit,
                low,
                high,
                abnormal,
                critical,
                interpretation
        );
    }

    public static LabResultItem ofText(String paramCode, String paramName, String loincCode,
                                       String textVal, boolean abnormal, boolean critical, String interpretation) {
        return new LabResultItem(
                UUID.randomUUID(),
                paramCode,
                paramName,
                loincCode,
                null,
                textVal,
                null,
                null,
                null,
                abnormal,
                critical,
                interpretation
        );
    }
}
