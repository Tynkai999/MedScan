package com.medscan.dashboard;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur JSON zéro-dépendance pour les données du tableau de bord multi-acteurs.
 */
public final class DashboardJsonMapper {

    private DashboardJsonMapper() {
    }

    public static String toJson(ActorDashboardStats stats) {
        if (stats == null) return "null";

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"role\":\"").append(JsonHelper.escape(stats.role())).append("\",");
        sb.append("\"userId\":\"").append(stats.userId()).append("\",");
        sb.append("\"username\":\"").append(JsonHelper.escape(stats.username())).append("\",");
        sb.append("\"tenantId\":\"").append(stats.tenantId()).append("\",");
        sb.append("\"tenantName\":\"").append(JsonHelper.escape(stats.tenantName())).append("\",");

        // KPIs
        sb.append("\"kpis\":").append(mapToJson(stats.kpis())).append(",");

        // Critical Alerts
        sb.append("\"criticalAlerts\":[");
        sb.append(stats.criticalAlerts().stream().map(a -> {
            StringBuilder asb = new StringBuilder("{");
            asb.append("\"id\":\"").append(a.id()).append("\",");
            asb.append("\"patientId\":\"").append(a.patientId()).append("\",");
            asb.append("\"patientName\":\"").append(JsonHelper.escape(a.patientName())).append("\",");
            asb.append("\"alertType\":\"").append(JsonHelper.escape(a.alertType())).append("\",");
            asb.append("\"severity\":\"").append(JsonHelper.escape(a.severity())).append("\",");
            asb.append("\"summary\":\"").append(JsonHelper.escape(a.summary())).append("\",");
            asb.append("\"recordedAt\":\"").append(JsonHelper.escape(a.recordedAt())).append("\"");
            asb.append("}");
            return asb.toString();
        }).collect(Collectors.joining(",")));
        sb.append("],");

        // Recent Activities
        sb.append("\"recentActivities\":[");
        sb.append(stats.recentActivities().stream().map(act -> {
            StringBuilder rsb = new StringBuilder("{");
            rsb.append("\"id\":\"").append(act.id()).append("\",");
            rsb.append("\"timestamp\":\"").append(JsonHelper.escape(act.timestamp())).append("\",");
            rsb.append("\"activityType\":\"").append(JsonHelper.escape(act.activityType())).append("\",");
            rsb.append("\"title\":\"").append(JsonHelper.escape(act.title())).append("\",");
            rsb.append("\"description\":\"").append(JsonHelper.escape(act.description())).append("\",");
            rsb.append("\"actorName\":\"").append(JsonHelper.escape(act.actorName())).append("\",");
            rsb.append("\"status\":\"").append(JsonHelper.escape(act.status())).append("\"");
            rsb.append("}");
            return rsb.toString();
        }).collect(Collectors.joining(",")));
        sb.append("],");

        // Charts Data
        sb.append("\"chartsData\":").append(mapToJson(stats.chartsData()));

        sb.append("}");
        return sb.toString();
    }

    private static String mapToJson(Map<String, Object> map) {
        if (map == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(JsonHelper.escape(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object val) {
        if (val == null) return "null";
        if (val instanceof Number || val instanceof Boolean) {
            return val.toString();
        }
        if (val instanceof String) {
            return "\"" + JsonHelper.escape((String) val) + "\"";
        }
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            return "[" + list.stream().map(DashboardJsonMapper::valueToJson).collect(Collectors.joining(",")) + "]";
        }
        if (val instanceof Map) {
            return mapToJson((Map<String, Object>) val);
        }
        return "\"" + JsonHelper.escape(val.toString()) + "\"";
    }
}
