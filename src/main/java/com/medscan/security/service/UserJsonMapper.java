package com.medscan.security.service;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur JSON pour les comptes utilisateurs et membres du personnel soignant.
 * Garantit qu'aucun mot de passe ou hachage sensible n'est exposé.
 */
public final class UserJsonMapper {

    private UserJsonMapper() {}

    public static String toJson(ActorAccount u) {
        if (u == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"userId\":\"").append(u.userId()).append("\",");
        sb.append("\"username\":\"").append(JsonHelper.escape(u.username())).append("\",");
        sb.append("\"email\":\"").append(JsonHelper.escape(u.email())).append("\",");
        sb.append("\"displayName\":\"").append(JsonHelper.escape(u.displayName())).append("\",");
        sb.append("\"tenantId\":\"").append(u.tenantId()).append("\",");
        sb.append("\"tenantCode\":\"").append(JsonHelper.escape(u.tenantCode())).append("\",");
        sb.append("\"roles\":[")
                .append(u.roles().stream().map(r -> "\"" + JsonHelper.escape(r) + "\"").collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"permissions\":[")
                .append(u.permissions().stream().map(p -> "\"" + JsonHelper.escape(p) + "\"").collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"status\":\"ACTIVE\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toListJson(List<ActorAccount> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(UserJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }
}
