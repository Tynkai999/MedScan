package com.medscan.interop.fhir;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.medscan.clinical.VitalSigns;
import com.medscan.security.jwt.JsonHelper;

/**
 * Mappeur standard HL7 FHIR R4 Observation (MOD-07).
 * Traduit les constantes vitales MedScan en ressources FHIR Observation codées en nomenclature LOINC & UCUM.
 */
public final class FhirObservationMapper {

    private FhirObservationMapper() {}

    /**
     * Génère l'Observation standard FHIR R4 pour le panel de Tension Artérielle (LOINC 85354-9).
     */
    public static String toBloodPressureObservation(VitalSigns v, String patientName) {
        if (v == null || v.systolicBp() == null || v.diastolicBp() == null) return null;

        return "{\n" +
                "  \"resourceType\": \"Observation\",\n" +
                "  \"id\": \"" + v.id() + "-bp\",\n" +
                "  \"status\": \"final\",\n" +
                "  \"category\": [\n" +
                "    {\n" +
                "      \"coding\": [\n" +
                "        {\n" +
                "          \"system\": \"" + FhirConstants.SYSTEM_OBS_CATEGORY + "\",\n" +
                "          \"code\": \"vital-signs\",\n" +
                "          \"display\": \"Vital Signs\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"code\": {\n" +
                "    \"coding\": [\n" +
                "      {\n" +
                "        \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                "        \"code\": \"" + FhirConstants.LOINC_BP_PANEL + "\",\n" +
                "        \"display\": \"Blood pressure panel with all children optional\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"text\": \"Tension Artérielle Systolique / Diastolique\"\n" +
                "  },\n" +
                "  \"subject\": {\n" +
                "    \"reference\": \"Patient/" + v.patientId() + "\",\n" +
                "    \"display\": \"" + (patientName != null ? JsonHelper.escape(patientName) : "Patient") + "\"\n" +
                "  },\n" +
                "  \"effectiveDateTime\": \"" + (v.recordedAt() != null ? v.recordedAt() : Instant.now()) + "\",\n" +
                "  \"component\": [\n" +
                "    {\n" +
                "      \"code\": {\n" +
                "        \"coding\": [\n" +
                "          {\n" +
                "            \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                "            \"code\": \"" + FhirConstants.LOINC_BP_SYSTOLIC + "\",\n" +
                "            \"display\": \"Systolic blood pressure\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      \"valueQuantity\": {\n" +
                "        \"value\": " + v.systolicBp() + ",\n" +
                "        \"unit\": \"mmHg\",\n" +
                "        \"system\": \"" + FhirConstants.SYSTEM_UCUM + "\",\n" +
                "        \"code\": \"mm[Hg]\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"code\": {\n" +
                "        \"coding\": [\n" +
                "          {\n" +
                "            \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                "            \"code\": \"" + FhirConstants.LOINC_BP_DIASTOLIC + "\",\n" +
                "            \"display\": \"Diastolic blood pressure\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      \"valueQuantity\": {\n" +
                "        \"value\": " + v.diastolicBp() + ",\n" +
                "        \"unit\": \"mmHg\",\n" +
                "        \"system\": \"" + FhirConstants.SYSTEM_UCUM + "\",\n" +
                "        \"code\": \"mm[Hg]\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    /**
     * Génère l'Observation standard FHIR R4 pour la Fréquence Cardiaque (LOINC 8867-4).
     */
    public static String toHeartRateObservation(VitalSigns v, String patientName) {
        if (v == null || v.heartRate() == null) return null;

        return "{\n" +
                "  \"resourceType\": \"Observation\",\n" +
                "  \"id\": \"" + v.id() + "-hr\",\n" +
                "  \"status\": \"final\",\n" +
                "  \"category\": [\n" +
                "    {\n" +
                "      \"coding\": [\n" +
                "        {\n" +
                "          \"system\": \"" + FhirConstants.SYSTEM_OBS_CATEGORY + "\",\n" +
                "          \"code\": \"vital-signs\",\n" +
                "          \"display\": \"Vital Signs\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"code\": {\n" +
                "    \"coding\": [\n" +
                "      {\n" +
                "        \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                "        \"code\": \"" + FhirConstants.LOINC_HEART_RATE + "\",\n" +
                "        \"display\": \"Heart rate\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"text\": \"Fréquence Cardiaque\"\n" +
                "  },\n" +
                "  \"subject\": {\n" +
                "    \"reference\": \"Patient/" + v.patientId() + "\",\n" +
                "    \"display\": \"" + (patientName != null ? JsonHelper.escape(patientName) : "Patient") + "\"\n" +
                "  },\n" +
                "  \"effectiveDateTime\": \"" + (v.recordedAt() != null ? v.recordedAt() : Instant.now()) + "\",\n" +
                "  \"valueQuantity\": {\n" +
                "    \"value\": " + v.heartRate() + ",\n" +
                "    \"unit\": \"/min\",\n" +
                "    \"system\": \"" + FhirConstants.SYSTEM_UCUM + "\",\n" +
                "    \"code\": \"/min\"\n" +
                "  }\n" +
                "}";
    }

    /**
     * Génère l'Observation standard FHIR R4 pour la Température Corporelle (LOINC 8310-5).
     */
    public static String toTemperatureObservation(VitalSigns v, String patientName) {
        if (v == null || v.temperature() == null) return null;

        return "{\n" +
                "  \"resourceType\": \"Observation\",\n" +
                "  \"id\": \"" + v.id() + "-temp\",\n" +
                "  \"status\": \"final\",\n" +
                "  \"category\": [\n" +
                "    {\n" +
                "      \"coding\": [\n" +
                "        {\n" +
                "          \"system\": \"" + FhirConstants.SYSTEM_OBS_CATEGORY + "\",\n" +
                "          \"code\": \"vital-signs\",\n" +
                "          \"display\": \"Vital Signs\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"code\": {\n" +
                "    \"coding\": [\n" +
                "      {\n" +
                "        \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                "        \"code\": \"" + FhirConstants.LOINC_BODY_TEMP + "\",\n" +
                "        \"display\": \"Body temperature\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"text\": \"Température Corporelle\"\n" +
                "  },\n" +
                "  \"subject\": {\n" +
                "    \"reference\": \"Patient/" + v.patientId() + "\",\n" +
                "    \"display\": \"" + (patientName != null ? JsonHelper.escape(patientName) : "Patient") + "\"\n" +
                "  },\n" +
                "  \"effectiveDateTime\": \"" + (v.recordedAt() != null ? v.recordedAt() : Instant.now()) + "\",\n" +
                "  \"valueQuantity\": {\n" +
                "    \"value\": " + v.temperature() + ",\n" +
                "    \"unit\": \"Cel\",\n" +
                "    \"system\": \"" + FhirConstants.SYSTEM_UCUM + "\",\n" +
                "    \"code\": \"Cel\"\n" +
                "  }\n" +
                "}";
    }

    /**
     * Génère l'Observation standard FHIR R4 pour l'Indice de Masse Corporelle (LOINC 39156-5).
     */
    public static String toBmiObservation(VitalSigns v, String patientName) {
        if (v == null || v.bmi() == null) return null;

        return "{\n" +
                "  \"resourceType\": \"Observation\",\n" +
                "  \"id\": \"" + v.id() + "-bmi\",\n" +
                "  \"status\": \"final\",\n" +
                "  \"category\": [\n" +
                "    {\n" +
                "      \"coding\": [\n" +
                "        {\n" +
                "          \"system\": \"" + FhirConstants.SYSTEM_OBS_CATEGORY + "\",\n" +
                "          \"code\": \"vital-signs\",\n" +
                "          \"display\": \"Vital Signs\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"code\": {\n" +
                "    \"coding\": [\n" +
                "      {\n" +
                "        \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                "        \"code\": \"" + FhirConstants.LOINC_BMI + "\",\n" +
                "        \"display\": \"Body mass index (BMI) [Ratio]\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"text\": \"Indice de Masse Corporelle (IMC)\"\n" +
                "  },\n" +
                "  \"subject\": {\n" +
                "    \"reference\": \"Patient/" + v.patientId() + "\",\n" +
                "    \"display\": \"" + (patientName != null ? JsonHelper.escape(patientName) : "Patient") + "\"\n" +
                "  },\n" +
                "  \"effectiveDateTime\": \"" + (v.recordedAt() != null ? v.recordedAt() : Instant.now()) + "\",\n" +
                "  \"valueQuantity\": {\n" +
                "    \"value\": " + v.bmi() + ",\n" +
                "    \"unit\": \"kg/m2\",\n" +
                "    \"system\": \"" + FhirConstants.SYSTEM_UCUM + "\",\n" +
                "    \"code\": \"kg/m2\"\n" +
                "  }" + (v.bmiCategory() != null ? ",\n  \"interpretation\": [{\n    \"text\": \"" + JsonHelper.escape(v.bmiCategory()) + "\"\n  }]" : "") + "\n" +
                "}";
    }

    /**
     * Génère l'Observation standard FHIR R4 pour la Saturation en Oxygène SpO2 (LOINC 2708-6).
     */
    public static String toOxygenSaturationObservation(VitalSigns v, String patientName) {
        if (v == null || v.oxygenSaturation() == null) return null;

        return "{\n" +
                "  \"resourceType\": \"Observation\",\n" +
                "  \"id\": \"" + v.id() + "-spo2\",\n" +
                "  \"status\": \"final\",\n" +
                "  \"category\": [\n" +
                "    {\n" +
                "      \"coding\": [\n" +
                "        {\n" +
                "          \"system\": \"" + FhirConstants.SYSTEM_OBS_CATEGORY + "\",\n" +
                "          \"code\": \"vital-signs\",\n" +
                "          \"display\": \"Vital Signs\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"code\": {\n" +
                "    \"coding\": [\n" +
                "      {\n" +
                "        \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                "        \"code\": \"" + FhirConstants.LOINC_SPO2 + "\",\n" +
                "        \"display\": \"Oxygen saturation in Arterial blood by Pulse oximetry\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"text\": \"Saturation en Oxygène (SpO2)\"\n" +
                "  },\n" +
                "  \"subject\": {\n" +
                "    \"reference\": \"Patient/" + v.patientId() + "\",\n" +
                "    \"display\": \"" + (patientName != null ? JsonHelper.escape(patientName) : "Patient") + "\"\n" +
                "  },\n" +
                "  \"effectiveDateTime\": \"" + (v.recordedAt() != null ? v.recordedAt() : Instant.now()) + "\",\n" +
                "  \"valueQuantity\": {\n" +
                "    \"value\": " + v.oxygenSaturation() + ",\n" +
                "    \"unit\": \"%\",\n" +
                "    \"system\": \"" + FhirConstants.SYSTEM_UCUM + "\",\n" +
                "    \"code\": \"%\"\n" +
                "  }\n" +
                "}";
    }

    /**
     * Convertit toutes les composantes physiologiques d'un VitalSigns en liste d'Observations FHIR distinctes.
     */
    public static List<String> toAllObservations(VitalSigns v, String patientName) {
        List<String> list = new ArrayList<>();
        String bp = toBloodPressureObservation(v, patientName);
        if (bp != null) list.add(bp);

        String hr = toHeartRateObservation(v, patientName);
        if (hr != null) list.add(hr);

        String temp = toTemperatureObservation(v, patientName);
        if (temp != null) list.add(temp);

        String bmi = toBmiObservation(v, patientName);
        if (bmi != null) list.add(bmi);

        String spo2 = toOxygenSaturationObservation(v, patientName);
        if (spo2 != null) list.add(spo2);

        return list;
    }
}
