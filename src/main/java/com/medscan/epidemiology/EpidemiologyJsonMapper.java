package com.medscan.epidemiology;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

public final class EpidemiologyJsonMapper {

    private EpidemiologyJsonMapper() {}

    public static String caseToJson(EpidemiologicalCase c) {
        if (c == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(c.id()).append("\",");
        sb.append("\"tenantId\":\"").append(c.tenantId()).append("\",");
        sb.append("\"region\":\"").append(JsonHelper.escape(c.region())).append("\",");
        sb.append("\"district\":\"").append(JsonHelper.escape(c.district())).append("\",");
        sb.append("\"diseaseCode\":\"").append(JsonHelper.escape(c.diseaseCode())).append("\",");
        sb.append("\"diseaseName\":\"").append(JsonHelper.escape(c.diseaseName())).append("\",");
        sb.append("\"detectionSource\":\"").append(JsonHelper.escape(c.detectionSource())).append("\",");
        sb.append("\"patientAge\":").append(c.patientAge()).append(",");
        sb.append("\"patientGender\":\"").append(JsonHelper.escape(c.patientGender())).append("\",");
        sb.append("\"outcome\":\"").append(JsonHelper.escape(c.outcome())).append("\",");
        sb.append("\"reportedAt\":\"").append(c.reportedAt()).append("\",");
        sb.append("\"reportedBy\":\"").append(c.reportedBy()).append("\",");
        sb.append("\"dhis2TrackedEntityId\":").append(c.dhis2TrackedEntityId() != null ? "\"" + JsonHelper.escape(c.dhis2TrackedEntityId()) + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }

    public static String casesListToJson(List<EpidemiologicalCase> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(EpidemiologyJsonMapper::caseToJson).collect(Collectors.joining(",")) + "]";
    }

    @SuppressWarnings("unchecked")
    public static String summaryToJson(Map<String, Object> summary) {
        if (summary == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"totalCases\":").append(summary.get("totalCases")).append(",");
        sb.append("\"generatedAt\":\"").append(summary.get("generatedAt")).append("\",");

        // casesByDisease
        sb.append("\"casesByDisease\":{");
        Map<String, Long> byDisease = (Map<String, Long>) summary.get("casesByDisease");
        if (byDisease != null) {
            sb.append(byDisease.entrySet().stream()
                    .map(e -> "\"" + JsonHelper.escape(e.getKey()) + "\":" + e.getValue())
                    .collect(Collectors.joining(",")));
        }
        sb.append("},");

        // casesByRegion
        sb.append("\"casesByRegion\":{");
        Map<String, Long> byRegion = (Map<String, Long>) summary.get("casesByRegion");
        if (byRegion != null) {
            sb.append(byRegion.entrySet().stream()
                    .map(e -> "\"" + JsonHelper.escape(e.getKey()) + "\":" + e.getValue())
                    .collect(Collectors.joining(",")));
        }
        sb.append("},");

        // casesByOutcome
        sb.append("\"casesByOutcome\":{");
        Map<String, Long> byOutcome = (Map<String, Long>) summary.get("casesByOutcome");
        if (byOutcome != null) {
            sb.append(byOutcome.entrySet().stream()
                    .map(e -> "\"" + JsonHelper.escape(e.getKey()) + "\":" + e.getValue())
                    .collect(Collectors.joining(",")));
        }
        sb.append("},");

        // activeEpidemicAlerts
        sb.append("\"activeEpidemicAlerts\":[");
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) summary.get("activeEpidemicAlerts");
        if (alerts != null) {
            List<String> alertBlocks = alerts.stream().map(a ->
                    "{\"diseaseCode\":\"" + JsonHelper.escape((String) a.get("diseaseCode")) + "\"," +
                            "\"caseCount\":" + a.get("caseCount") + "," +
                            "\"threshold\":" + a.get("threshold") + "," +
                            "\"status\":\"" + JsonHelper.escape((String) a.get("status")) + "\"}"
            ).collect(Collectors.toList());
            sb.append(String.join(",", alertBlocks));
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }
}
