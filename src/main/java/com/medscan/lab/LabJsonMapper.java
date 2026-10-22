package com.medscan.lab;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

public final class LabJsonMapper {

    private LabJsonMapper() {}

    public static String itemToJson(LabResultItem item) {
        if (item == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(item.id()).append("\",");
        sb.append("\"paramCode\":\"").append(JsonHelper.escape(item.paramCode())).append("\",");
        sb.append("\"paramName\":\"").append(JsonHelper.escape(item.paramName())).append("\",");
        sb.append("\"loincCode\":").append(item.loincCode() != null ? "\"" + JsonHelper.escape(item.loincCode()) + "\"" : "null").append(",");
        sb.append("\"numericValue\":").append(item.numericValue() != null ? item.numericValue() : "null").append(",");
        sb.append("\"textValue\":").append(item.textValue() != null ? "\"" + JsonHelper.escape(item.textValue()) + "\"" : "null").append(",");
        sb.append("\"unit\":").append(item.unit() != null ? "\"" + JsonHelper.escape(item.unit()) + "\"" : "null").append(",");
        sb.append("\"refIntervalLow\":").append(item.refIntervalLow() != null ? item.refIntervalLow() : "null").append(",");
        sb.append("\"refIntervalHigh\":").append(item.refIntervalHigh() != null ? item.refIntervalHigh() : "null").append(",");
        sb.append("\"isAbnormal\":").append(item.isAbnormal()).append(",");
        sb.append("\"isCritical\":").append(item.isCritical()).append(",");
        sb.append("\"interpretation\":").append(item.interpretation() != null ? "\"" + JsonHelper.escape(item.interpretation()) + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }

    public static String orderToJson(LabOrder order) {
        if (order == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(order.id()).append("\",");
        sb.append("\"orderCode\":\"").append(JsonHelper.escape(order.orderCode())).append("\",");
        sb.append("\"tenantId\":\"").append(order.tenantId()).append("\",");
        sb.append("\"patientId\":\"").append(order.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(order.patientName())).append("\",");
        sb.append("\"prescriberId\":\"").append(order.prescriberId()).append("\",");
        sb.append("\"prescriberName\":\"").append(JsonHelper.escape(order.prescriberName())).append("\",");
        sb.append("\"technicianId\":").append(order.technicianId() != null ? "\"" + order.technicianId() + "\"" : "null").append(",");
        sb.append("\"technicianName\":").append(order.technicianName() != null ? "\"" + JsonHelper.escape(order.technicianName()) + "\"" : "null").append(",");
        sb.append("\"testCategory\":\"").append(JsonHelper.escape(order.testCategory())).append("\",");
        sb.append("\"testName\":\"").append(JsonHelper.escape(order.testName())).append("\",");
        sb.append("\"priority\":\"").append(JsonHelper.escape(order.priority())).append("\",");
        sb.append("\"status\":\"").append(JsonHelper.escape(order.status())).append("\",");
        sb.append("\"clinicalIndication\":").append(order.clinicalIndication() != null ? "\"" + JsonHelper.escape(order.clinicalIndication()) + "\"" : "null").append(",");
        sb.append("\"sampleCollectedAt\":").append(order.sampleCollectedAt() != null ? "\"" + order.sampleCollectedAt() + "\"" : "null").append(",");
        sb.append("\"validatedAt\":").append(order.validatedAt() != null ? "\"" + order.validatedAt() + "\"" : "null").append(",");
        sb.append("\"hasCriticalAlert\":").append(order.hasCriticalAlert()).append(",");
        sb.append("\"createdAt\":\"").append(order.createdAt()).append("\",");
        sb.append("\"results\":[");
        if (order.results() != null) {
            sb.append(order.results().stream().map(LabJsonMapper::itemToJson).collect(Collectors.joining(",")));
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    public static String ordersListToJson(List<LabOrder> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(LabJsonMapper::orderToJson).collect(Collectors.joining(",")) + "]";
    }
}
