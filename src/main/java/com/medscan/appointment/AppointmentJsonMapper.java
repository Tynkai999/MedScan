package com.medscan.appointment;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

public final class AppointmentJsonMapper {

    private AppointmentJsonMapper() {}

    public static String toJson(Appointment a) {
        if (a == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(a.id()).append("\",");
        sb.append("\"tenantId\":\"").append(a.tenantId()).append("\",");
        sb.append("\"patientId\":\"").append(a.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(a.patientName())).append("\",");
        sb.append("\"practitionerId\":\"").append(a.practitionerId()).append("\",");
        sb.append("\"practitionerName\":\"").append(JsonHelper.escape(a.practitionerName())).append("\",");
        sb.append("\"practitionerRole\":\"").append(JsonHelper.escape(a.practitionerRole())).append("\",");
        sb.append("\"appointmentType\":\"").append(JsonHelper.escape(a.appointmentType())).append("\",");
        sb.append("\"scheduledAt\":\"").append(a.scheduledAt()).append("\",");
        sb.append("\"durationMinutes\":").append(a.durationMinutes()).append(",");
        sb.append("\"status\":\"").append(JsonHelper.escape(a.status())).append("\",");
        sb.append("\"reason\":\"").append(JsonHelper.escape(a.reason() != null ? a.reason() : "")).append("\",");
        sb.append("\"clinicalNotes\":\"").append(JsonHelper.escape(a.clinicalNotes() != null ? a.clinicalNotes() : "")).append("\",");
        sb.append("\"teleconsultationRoomId\":").append(a.teleconsultationRoomId() != null ? "\"" + JsonHelper.escape(a.teleconsultationRoomId()) + "\"" : "null").append(",");
        sb.append("\"teleconsultationJoinUrl\":").append(a.teleconsultationJoinUrl() != null ? "\"" + JsonHelper.escape(a.teleconsultationJoinUrl()) + "\"" : "null").append(",");
        sb.append("\"createdAt\":\"").append(a.createdAt()).append("\",");
        sb.append("\"updatedAt\":\"").append(a.updatedAt()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toJsonList(List<Appointment> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(AppointmentJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }
}
