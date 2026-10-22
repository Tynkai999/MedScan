package com.medscan.interop.fhir;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.medscan.clinical.Patient;
import com.medscan.security.jwt.JsonHelper;

/**
 * Mappeur bidirectionnel entre l'entité Patient MedScan et la ressource standard HL7 FHIR R4 Patient (MOD-07).
 */
public final class FhirPatientMapper {

    private FhirPatientMapper() {}

    /**
     * Convertit un Patient interne MedScan en ressource JSON FHIR R4 standard.
     */
    public static String toFhirJson(Patient p, String baseUrl) {
        if (p == null) return "null";
        String base = (baseUrl != null) ? baseUrl.replaceAll("/+$", "") : "https://medscan-sluw.onrender.com/medscan/api/fhir/r4";

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"resourceType\": \"Patient\",\n");
        sb.append("  \"id\": \"").append(p.id()).append("\",\n");
        sb.append("  \"meta\": {\n");
        sb.append("    \"versionId\": \"1\",\n");
        sb.append("    \"lastUpdated\": \"").append(p.updatedAt() != null ? p.updatedAt() : Instant.now()).append("\",\n");
        sb.append("    \"profile\": [\"http://hl7.org/fhir/StructureDefinition/Patient\"]\n");
        sb.append("  },\n");
        sb.append("  \"text\": {\n");
        sb.append("    \"status\": \"generated\",\n");
        sb.append("    \"div\": \"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\">").append(JsonHelper.escape(p.fullName())).append(" (").append(JsonHelper.escape(p.nationalId())).append(")</div>\"\n");
        sb.append("  },\n");
        sb.append("  \"identifier\": [\n");
        sb.append("    {\n");
        sb.append("      \"use\": \"official\",\n");
        sb.append("      \"system\": \"").append(FhirConstants.SYSTEM_NATIONAL_ID).append("\",\n");
        sb.append("      \"value\": \"").append(JsonHelper.escape(p.nationalId())).append("\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"active\": true,\n");
        sb.append("  \"name\": [\n");
        sb.append("    {\n");
        sb.append("      \"use\": \"official\",\n");
        sb.append("      \"family\": \"").append(JsonHelper.escape(p.lastName())).append("\",\n");
        sb.append("      \"given\": [\"").append(JsonHelper.escape(p.firstName())).append("\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");

        if (p.phone() != null && !p.phone().isBlank()) {
            sb.append("  \"telecom\": [\n");
            sb.append("    {\n");
            sb.append("      \"system\": \"phone\",\n");
            sb.append("      \"value\": \"").append(JsonHelper.escape(p.phone())).append("\",\n");
            sb.append("      \"use\": \"mobile\"\n");
            sb.append("    }\n");
            sb.append("  ],\n");
        }

        String gender = "other";
        if (p.gender() != null) {
            String g = p.gender().trim().toLowerCase();
            if (g.startsWith("m") || g.startsWith("h")) gender = "male";
            else if (g.startsWith("f")) gender = "female";
        }
        sb.append("  \"gender\": \"").append(gender).append("\",\n");

        if (p.birthDate() != null && !p.birthDate().isBlank()) {
            sb.append("  \"birthDate\": \"").append(JsonHelper.escape(p.birthDate())).append("\",\n");
        }

        if (p.emergencyContact() != null && !p.emergencyContact().isBlank()) {
            sb.append("  \"contact\": [\n");
            sb.append("    {\n");
            sb.append("      \"relationship\": [\n");
            sb.append("        {\n");
            sb.append("          \"coding\": [\n");
            sb.append("            {\n");
            sb.append("              \"system\": \"").append(FhirConstants.SYSTEM_CONTACT_ROLE).append("\",\n");
            sb.append("              \"code\": \"C\",\n");
            sb.append("              \"display\": \"Emergency Contact\"\n");
            sb.append("            }\n");
            sb.append("          ]\n");
            sb.append("        }\n");
            sb.append("      ],\n");
            sb.append("      \"name\": {\n");
            sb.append("        \"text\": \"").append(JsonHelper.escape(p.emergencyContact())).append("\"\n");
            sb.append("      }\n");
            sb.append("    }\n");
            sb.append("  ],\n");
        }

        // Extensions FHIR : groupe sanguin, allergies, IMC
        sb.append("  \"extension\": [\n");
        List<String> extensions = new ArrayList<>();
        if (p.bloodGroup() != null && !p.bloodGroup().isBlank()) {
            extensions.add("    {\n      \"url\": \"" + FhirConstants.EXT_BLOOD_GROUP + "\",\n      \"valueString\": \"" + JsonHelper.escape(p.bloodGroup()) + "\"\n    }");
        }
        if (p.allergies() != null && !p.allergies().isEmpty()) {
            extensions.add("    {\n      \"url\": \"" + FhirConstants.EXT_ALLERGIES + "\",\n      \"valueString\": \"" + JsonHelper.escape(String.join(", ", p.allergies())) + "\"\n    }");
        }
        if (p.chronicConditions() != null && !p.chronicConditions().isEmpty()) {
            extensions.add("    {\n      \"url\": \"" + FhirConstants.EXT_CHRONIC + "\",\n      \"valueString\": \"" + JsonHelper.escape(String.join(", ", p.chronicConditions())) + "\"\n    }");
        }
        if (p.bmi() != null) {
            extensions.add("    {\n      \"url\": \"" + FhirConstants.EXT_BMI + "\",\n      \"valueDecimal\": " + p.bmi() + "\n    }");
        }
        if (p.bmiCategory() != null) {
            extensions.add("    {\n      \"url\": \"" + FhirConstants.EXT_BMI_CATEGORY + "\",\n      \"valueString\": \"" + JsonHelper.escape(p.bmiCategory()) + "\"\n    }");
        }
        sb.append(String.join(",\n", extensions));
        sb.append("\n  ]\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * Analyse une ressource FHIR R4 Patient entrante et la transforme en entité Patient MedScan.
     */
    public static Patient parseFhirPatient(String fhirJson, UUID tenantId) {
        String idStr = JsonHelper.getString(fhirJson, "id");
        UUID id = null;
        if (idStr != null && !idStr.isBlank()) {
            try {
                id = UUID.fromString(idStr);
            } catch (Exception ignored) {}
        }
        if (id == null) {
            id = UUID.randomUUID();
        }

        String family = JsonHelper.getString(fhirJson, "family");
        if (family == null) family = "Nom";

        String given = JsonHelper.getString(fhirJson, "given");
        if (given == null) {
            List<String> givens = JsonHelper.getStringList(fhirJson, "given");
            given = (givens != null && !givens.isEmpty()) ? givens.get(0) : "Prénom";
        }

        String birthDate = JsonHelper.getString(fhirJson, "birthDate");
        if (birthDate == null || birthDate.isBlank()) birthDate = "1990-01-01";

        String genderRaw = JsonHelper.getString(fhirJson, "gender");
        String gender = "U";
        if (genderRaw != null) {
            if ("male".equalsIgnoreCase(genderRaw)) gender = "M";
            else if ("female".equalsIgnoreCase(genderRaw)) gender = "F";
        }

        String nationalId = JsonHelper.getString(fhirJson, "value");
        if (nationalId == null || nationalId.isBlank()) {
            nationalId = "BFA-" + (2026) + "-" + String.format("%06d", (int)(Math.random() * 900000 + 100000));
        }

        String phone = JsonHelper.getString(fhirJson, "value");
        if (phone != null && phone.equals(nationalId)) {
            // Tenter d'extraire la valeur spécifique au telecom
            phone = "";
        }

        String bloodGroup = "Inconnu";
        if (fhirJson.contains(FhirConstants.EXT_BLOOD_GROUP)) {
            bloodGroup = extractExtensionString(fhirJson, FhirConstants.EXT_BLOOD_GROUP);
        }

        List<String> allergies = new ArrayList<>();
        if (fhirJson.contains(FhirConstants.EXT_ALLERGIES)) {
            String allStr = extractExtensionString(fhirJson, FhirConstants.EXT_ALLERGIES);
            if (allStr != null && !allStr.isBlank()) {
                for (String a : allStr.split(",")) {
                    allergies.add(a.trim());
                }
            }
        }

        List<String> chronic = new ArrayList<>();
        if (fhirJson.contains(FhirConstants.EXT_CHRONIC)) {
            String chStr = extractExtensionString(fhirJson, FhirConstants.EXT_CHRONIC);
            if (chStr != null && !chStr.isBlank()) {
                for (String c : chStr.split(",")) {
                    chronic.add(c.trim());
                }
            }
        }

        return new Patient(
                id,
                nationalId,
                given,
                family,
                birthDate,
                null,
                gender,
                bloodGroup,
                phone != null ? phone : "",
                "Contact d'urgence",
                allergies,
                chronic,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                tenantId,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private static String extractExtensionString(String json, String extUrl) {
        int idx = json.indexOf(extUrl);
        if (idx == -1) return null;
        String tail = json.substring(idx);
        return JsonHelper.getString(tail, "valueString");
    }
}
