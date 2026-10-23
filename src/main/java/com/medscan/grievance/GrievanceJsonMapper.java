package com.medscan.grievance;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Mappeur JSON pour les réclamations et avis patients hospitaliers (MOD-18).
 */
public final class GrievanceJsonMapper {

    private GrievanceJsonMapper() {}

    public static String grievanceToJson(PatientGrievance g) {
        if (g == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(g.id()).append("\",");
        sb.append("\"grievanceCode\":\"").append(JsonHelper.escape(g.grievanceCode())).append("\",");
        sb.append("\"patientId\":\"").append(g.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(g.patientName())).append("\",");
        sb.append("\"patientPhone\":").append(g.patientPhone() != null ? "\"" + JsonHelper.escape(g.patientPhone()) + "\"" : "null").append(",");
        sb.append("\"doctorId\":\"").append(g.doctorId()).append("\",");
        sb.append("\"doctorName\":\"").append(JsonHelper.escape(g.doctorName())).append("\",");
        sb.append("\"tenantId\":\"").append(g.tenantId()).append("\",");
        sb.append("\"hospitalName\":\"").append(JsonHelper.escape(g.hospitalName())).append("\",");
        sb.append("\"consultationId\":").append(g.consultationId() != null ? "\"" + g.consultationId() + "\"" : "null").append(",");
        sb.append("\"grievanceType\":\"").append(JsonHelper.escape(g.grievanceType())).append("\",");
        sb.append("\"category\":\"").append(JsonHelper.escape(g.category())).append("\",");
        sb.append("\"rating\":").append(g.rating()).append(",");
        sb.append("\"severity\":\"").append(JsonHelper.escape(g.severity())).append("\",");
        sb.append("\"title\":\"").append(JsonHelper.escape(g.title())).append("\",");
        sb.append("\"description\":\"").append(JsonHelper.escape(g.description())).append("\",");
        sb.append("\"status\":\"").append(JsonHelper.escape(g.status())).append("\",");
        sb.append("\"adminNotes\":").append(g.adminNotes() != null ? "\"" + JsonHelper.escape(g.adminNotes()) + "\"" : "null").append(",");
        sb.append("\"adminActionTaken\":").append(g.adminActionTaken() != null ? "\"" + JsonHelper.escape(g.adminActionTaken()) + "\"" : "null").append(",");
        sb.append("\"reviewedByAdminId\":").append(g.reviewedByAdminId() != null ? "\"" + g.reviewedByAdminId() + "\"" : "null").append(",");
        sb.append("\"reviewedByAdminName\":").append(g.reviewedByAdminName() != null ? "\"" + JsonHelper.escape(g.reviewedByAdminName()) + "\"" : "null").append(",");
        sb.append("\"reviewedAt\":").append(g.reviewedAt() != null ? "\"" + g.reviewedAt() + "\"" : "null").append(",");
        sb.append("\"createdAt\":\"").append(g.createdAt()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String listToJson(List<PatientGrievance> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(GrievanceJsonMapper::grievanceToJson).collect(Collectors.joining(",")) + "]";
    }
}
