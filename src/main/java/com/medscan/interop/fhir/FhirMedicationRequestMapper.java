package com.medscan.interop.fhir;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.medscan.clinical.Prescription;
import com.medscan.clinical.PrescriptionItem;
import com.medscan.security.jwt.JsonHelper;

/**
 * Mappeur standard HL7 FHIR R4 MedicationRequest (MOD-07).
 * Traduit les ordonnances électroniques MedScan en prescriptions médicales numériques interopérables.
 */
public final class FhirMedicationRequestMapper {

    private FhirMedicationRequestMapper() {}

    public static String toFhirJson(Prescription rx, String baseUrl) {
        if (rx == null) return "null";

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"resourceType\": \"MedicationRequest\",\n");
        sb.append("  \"id\": \"").append(rx.id()).append("\",\n");
        sb.append("  \"meta\": {\n");
        sb.append("    \"versionId\": \"1\",\n");
        sb.append("    \"lastUpdated\": \"").append(rx.dispensedAt() != null ? rx.dispensedAt() : (rx.issuedAt() != null ? rx.issuedAt() : Instant.now())).append("\",\n");
        sb.append("    \"profile\": [\"http://hl7.org/fhir/StructureDefinition/MedicationRequest\"]\n");
        sb.append("  },\n");
        sb.append("  \"identifier\": [\n");
        sb.append("    {\n");
        sb.append("      \"use\": \"official\",\n");
        sb.append("      \"system\": \"").append(FhirConstants.SYSTEM_PRESCRIPTION_CODE).append("\",\n");
        sb.append("      \"value\": \"").append(JsonHelper.escape(rx.prescriptionCode())).append("\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");

        String status = "active";
        if ("DISPENSED".equalsIgnoreCase(rx.status())) status = "completed";
        else if ("CANCELLED".equalsIgnoreCase(rx.status())) status = "cancelled";
        sb.append("  \"status\": \"").append(status).append("\",\n");
        sb.append("  \"intent\": \"order\",\n");

        sb.append("  \"subject\": {\n");
        sb.append("    \"reference\": \"Patient/").append(rx.patientId()).append("\",\n");
        sb.append("    \"display\": \"").append(JsonHelper.escape(rx.patientName())).append("\"\n");
        sb.append("  },\n");

        sb.append("  \"authoredOn\": \"").append(rx.issuedAt() != null ? rx.issuedAt() : Instant.now()).append("\",\n");

        sb.append("  \"requester\": {\n");
        sb.append("    \"reference\": \"Practitioner/").append(rx.doctorId()).append("\",\n");
        sb.append("    \"display\": \"").append(JsonHelper.escape(rx.doctorName())).append("\"\n");
        sb.append("  },\n");

        // Lignes de traitement (Dosage Instructions)
        sb.append("  \"dosageInstruction\": [\n");
        List<String> dosages = new ArrayList<>();
        if (rx.items() != null) {
            for (PrescriptionItem item : rx.items()) {
                dosages.add("    {\n" +
                        "      \"text\": \"" + JsonHelper.escape(item.medicationName() + " - " + item.dosage() + ", " + item.frequency() + " pendant " + item.durationDays() + "j") + "\",\n" +
                        "      \"patientInstruction\": \"" + JsonHelper.escape(item.instructions() != null ? item.instructions() : "") + "\"\n" +
                        "    }");
            }
        }
        sb.append(String.join(",\n", dosages));
        sb.append("\n  ]");

        // Dispensation en officine si déjà délivrée
        if (rx.dispensedBy() != null && !rx.dispensedBy().isBlank()) {
            sb.append(",\n  \"dispenseRequest\": {\n");
            sb.append("    \"performer\": {\n");
            sb.append("      \"display\": \"").append(JsonHelper.escape(rx.dispensedBy())).append("\"\n");
            sb.append("    }\n");
            sb.append("  }\n");
        } else {
            sb.append("\n");
        }

        sb.append("}");
        return sb.toString();
    }
}
