package com.medscan.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalJsonMapper;
import com.medscan.clinical.Consultation;
import com.medscan.clinical.Patient;
import com.medscan.clinical.Prescription;
import com.medscan.clinical.VitalSigns;
import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur et désérialiseur JSON natif pour le module de synchronisation hors-ligne (MOD-10).
 * Zéro dépendance externe pour éliminer les risques de failles logicielles et classpath conflicts.
 */
public final class SyncJsonMapper {

    private SyncJsonMapper() {
    }

    // ==========================================
    // SÉRIALISATION DE LA FICHE D'URGENCE
    // ==========================================

    public static String toJson(EmergencyCard c) {
        if (c == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"patientId\":\"").append(c.patientId()).append("\",");
        sb.append("\"nationalId\":\"").append(JsonHelper.escape(c.nationalId())).append("\",");
        sb.append("\"fullName\":\"").append(JsonHelper.escape(c.fullName())).append("\",");
        sb.append("\"gender\":\"").append(JsonHelper.escape(c.gender())).append("\",");
        sb.append("\"birthDate\":\"").append(JsonHelper.escape(c.birthDate())).append("\",");
        sb.append("\"age\":").append(c.age()).append(",");
        sb.append("\"bloodGroup\":\"").append(JsonHelper.escape(c.bloodGroup())).append("\",");
        sb.append("\"allergies\":[")
                .append(c.allergies().stream().map(a -> "\"" + JsonHelper.escape(a) + "\"").collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"chronicConditions\":[")
                .append(c.chronicConditions().stream().map(cc -> "\"" + JsonHelper.escape(cc) + "\"").collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"emergencyContact\":\"").append(JsonHelper.escape(c.emergencyContact())).append("\",");
        sb.append("\"weightKg\":").append(c.weightKg()).append(",");
        sb.append("\"heightCm\":").append(c.heightCm()).append(",");
        sb.append("\"bmi\":").append(c.bmi()).append(",");
        sb.append("\"bmiCategory\":").append(c.bmiCategory() != null ? "\"" + JsonHelper.escape(c.bmiCategory()) + "\"" : "null").append(",");
        sb.append("\"lastBloodPressure\":").append(c.lastBloodPressure() != null ? "\"" + JsonHelper.escape(c.lastBloodPressure()) + "\"" : "null").append(",");
        sb.append("\"lastHeartRate\":").append(c.lastHeartRate()).append(",");
        sb.append("\"lastOxygenSaturation\":").append(c.lastOxygenSaturation()).append(",");
        sb.append("\"lastTriageLevel\":\"").append(JsonHelper.escape(c.lastTriageLevel())).append("\",");
        sb.append("\"lastVitalsRecordedAt\":").append(c.lastVitalsRecordedAt() != null ? "\"" + c.lastVitalsRecordedAt() + "\"" : "null").append(",");
        sb.append("\"activePrescriptionsCount\":").append(c.activePrescriptionsCount()).append(",");
        sb.append("\"generatedAt\":\"").append(c.generatedAt()).append("\",");
        sb.append("\"integrityHash\":\"").append(JsonHelper.escape(c.integrityHash())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toEmergencyCardListJson(List<EmergencyCard> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(SyncJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    // ==========================================
    // SÉRIALISATION DU PAQUET DE SYNCHRONISATION
    // ==========================================

    public static String toJson(SyncPackage pkg) {
        if (pkg == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"syncTimestamp\":\"").append(pkg.syncTimestamp()).append("\",");
        sb.append("\"sinceTimestamp\":").append(pkg.sinceTimestamp() != null ? "\"" + pkg.sinceTimestamp() + "\"" : "null").append(",");
        sb.append("\"totalRecords\":").append(pkg.totalRecords()).append(",");
        sb.append("\"patients\":").append(ClinicalJsonMapper.toPatientListJson(pkg.patients())).append(",");
        sb.append("\"vitalSigns\":").append(ClinicalJsonMapper.toVitalsListJson(pkg.vitalSigns())).append(",");
        sb.append("\"consultations\":").append(ClinicalJsonMapper.toConsultationListJson(pkg.consultations())).append(",");
        sb.append("\"prescriptions\":").append(ClinicalJsonMapper.toPrescriptionListJson(pkg.prescriptions())).append(",");
        sb.append("\"emergencyCards\":").append(toEmergencyCardListJson(pkg.emergencyCards()));
        sb.append("}");
        return sb.toString();
    }

    // ==========================================
    // SÉRIALISATION DES RÉSULTATS D'OPÉRATIONS
    // ==========================================

    public static String toJson(SyncOperationResult r) {
        if (r == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"operationId\":\"").append(r.operationId()).append("\",");
        sb.append("\"status\":\"").append(r.status()).append("\",");
        sb.append("\"entityType\":\"").append(JsonHelper.escape(r.entityType())).append("\",");
        sb.append("\"entityId\":").append(r.entityId() != null ? "\"" + r.entityId() + "\"" : "null").append(",");
        sb.append("\"message\":\"").append(JsonHelper.escape(r.message())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toOperationResultListJson(List<SyncOperationResult> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(SyncJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    // ==========================================
    // SÉRIALISATION DE LA RÉPONSE DE LOT
    // ==========================================

    public static String toJson(SyncBatchResponse resp) {
        if (resp == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"batchId\":\"").append(resp.batchId()).append("\",");
        sb.append("\"processedAt\":\"").append(resp.processedAt()).append("\",");
        sb.append("\"totalOperations\":").append(resp.totalOperations()).append(",");
        sb.append("\"appliedCount\":").append(resp.appliedCount()).append(",");
        sb.append("\"duplicateCount\":").append(resp.duplicateCount()).append(",");
        sb.append("\"errorCount\":").append(resp.errorCount()).append(",");
        sb.append("\"results\":").append(toOperationResultListJson(resp.results())).append(",");
        sb.append("\"delta\":").append(resp.delta() != null ? toJson(resp.delta()) : "null");
        sb.append("}");
        return sb.toString();
    }

    // ==========================================
    // DÉSÉRIALISATION DE LA REQUÊTE DE LOT
    // ==========================================

    public static SyncBatchRequest parseSyncBatchRequest(String body) {
        if (body == null || body.isBlank()) {
            return new SyncBatchRequest(UUID.randomUUID(), "UNKNOWN_DEVICE", Instant.now(), List.of(), null);
        }

        String batchIdStr = JsonHelper.getString(body, "batchId");
        UUID batchId = null;
        if (batchIdStr != null && !batchIdStr.isBlank()) {
            try {
                batchId = UUID.fromString(batchIdStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (batchId == null) {
            batchId = UUID.randomUUID();
        }

        String deviceId = JsonHelper.getString(body, "deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = "MOBILE_DEVICE";
        }

        String clientTimeStr = JsonHelper.getString(body, "clientTimestamp");
        Instant clientTime = Instant.now();
        if (clientTimeStr != null && !clientTimeStr.isBlank()) {
            try {
                clientTime = Instant.parse(clientTimeStr);
            } catch (Exception ignored) {
            }
        }

        String pullSinceStr = JsonHelper.getString(body, "pullSince");
        Instant pullSince = null;
        if (pullSinceStr != null && !pullSinceStr.isBlank()) {
            try {
                pullSince = Instant.parse(pullSinceStr);
            } catch (Exception ignored) {
            }
        }

        List<String> opObjects = extractArrayObjects(body, "operations");
        List<SyncOperation> operations = new ArrayList<>();

        for (String opJson : opObjects) {
            String opIdStr = JsonHelper.getString(opJson, "operationId");
            UUID opId = null;
            if (opIdStr != null && !opIdStr.isBlank()) {
                try {
                    opId = UUID.fromString(opIdStr);
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (opId == null) {
                opId = UUID.randomUUID();
            }

            String entityType = JsonHelper.getString(opJson, "entityType");
            String action = JsonHelper.getString(opJson, "action");

            String occurredStr = JsonHelper.getString(opJson, "occurredAt");
            Instant occurredAt = Instant.now();
            if (occurredStr != null && !occurredStr.isBlank()) {
                try {
                    occurredAt = Instant.parse(occurredStr);
                } catch (Exception ignored) {
                }
            }

            String payload = extractSubObject(opJson, "payload");

            operations.add(new SyncOperation(opId, entityType, action, occurredAt, payload));
        }

        return new SyncBatchRequest(batchId, deviceId, clientTime, operations, pullSince);
    }

    /**
     * Extrait une sous-structure JSON (objet `{...}`) associée à une clé.
     */
    public static String extractSubObject(String json, String key) {
        if (json == null || key == null) return "{}";
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx == -1) return "{}";
        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx == -1) return "{}";

        int openBrace = json.indexOf('{', colonIdx);
        if (openBrace == -1) {
            String str = JsonHelper.getString(json, key);
            return str != null ? str : "{}";
        }

        int depth = 0;
        boolean inQuotes = false;
        for (int i = openBrace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(openBrace, i + 1);
                    }
                }
            }
        }
        return "{}";
    }

    /**
     * Extrait la liste des objets JSON individuels composant un tableau `[ {...}, {...} ]`.
     */
    public static List<String> extractArrayObjects(String json, String arrayKey) {
        List<String> objects = new ArrayList<>();
        if (json == null || arrayKey == null) return objects;
        String pattern = "\"" + arrayKey + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx == -1) return objects;
        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx == -1) return objects;
        int arrayStart = json.indexOf('[', colonIdx);
        if (arrayStart == -1) return objects;

        int depth = 0;
        int objStart = -1;
        boolean inQuotes = false;
        for (int i = arrayStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) {
                        objStart = i;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart != -1) {
                        objects.add(json.substring(objStart, i + 1));
                        objStart = -1;
                    }
                } else if (c == ']' && depth == 0) {
                    break;
                }
            }
        }
        return objects;
    }
}
