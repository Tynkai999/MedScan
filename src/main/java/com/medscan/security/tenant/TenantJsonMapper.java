package com.medscan.security.tenant;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur et désérialiseur JSON pour les structures de santé (Tenant).
 */
public final class TenantJsonMapper {

    private TenantJsonMapper() {}

    public static String toJson(Tenant t) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(t.id()).append("\",");
        sb.append("\"code\":\"").append(JsonHelper.escape(t.code())).append("\",");
        sb.append("\"name\":\"").append(JsonHelper.escape(t.name())).append("\",");
        sb.append("\"type\":\"").append(JsonHelper.escape(t.type())).append("\",");
        sb.append("\"country\":\"").append(JsonHelper.escape(t.country())).append("\",");
        sb.append("\"city\":\"").append(JsonHelper.escape(t.city())).append("\",");
        sb.append("\"phone\":\"").append(JsonHelper.escape(t.phone())).append("\",");
        sb.append("\"email\":\"").append(JsonHelper.escape(t.email())).append("\",");
        sb.append("\"address\":\"").append(JsonHelper.escape(t.address())).append("\",");
        sb.append("\"status\":\"").append(JsonHelper.escape(t.status())).append("\",");
        sb.append("\"createdAt\":\"").append(t.createdAt()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toListJson(List<Tenant> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(TenantJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }
}
