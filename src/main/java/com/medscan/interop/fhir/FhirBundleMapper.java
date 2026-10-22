package com.medscan.interop.fhir;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.medscan.security.jwt.JsonHelper;

/**
 * Mappeur standard HL7 FHIR R4 Bundle (MOD-07).
 * Encapsule les listes de ressources en paquets interopérables searchset, collection ou transaction.
 */
public final class FhirBundleMapper {

    private FhirBundleMapper() {}

    /**
     * Crée un Bundle FHIR de type 'searchset' pour les résultats de recherche.
     */
    public static String toSearchSetBundle(List<String> resourceJsons, String searchUrl, int total) {
        return toBundle("searchset", resourceJsons, searchUrl, total);
    }

    /**
     * Crée un Bundle FHIR de type 'collection' pour l'export complet du dossier médical partagé.
     */
    public static String toCollectionBundle(List<String> resourceJsons, String bundleUrl) {
        return toBundle("collection", resourceJsons, bundleUrl, resourceJsons != null ? resourceJsons.size() : 0);
    }

    private static String toBundle(String type, List<String> resourceJsons, String url, int total) {
        UUID bundleId = UUID.randomUUID();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"resourceType\": \"Bundle\",\n");
        sb.append("  \"id\": \"").append(bundleId).append("\",\n");
        sb.append("  \"meta\": {\n");
        sb.append("    \"lastUpdated\": \"").append(Instant.now()).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"type\": \"").append(type).append("\",\n");
        sb.append("  \"total\": ").append(total).append(",\n");

        if (url != null && !url.isBlank()) {
            sb.append("  \"link\": [\n");
            sb.append("    {\n");
            sb.append("      \"relation\": \"self\",\n");
            sb.append("      \"url\": \"").append(JsonHelper.escape(url)).append("\"\n");
            sb.append("    }\n");
            sb.append("  ],\n");
        }

        sb.append("  \"entry\": [\n");
        List<String> entries = new ArrayList<>();
        if (resourceJsons != null) {
            for (String res : resourceJsons) {
                if (res == null || res.isBlank()) continue;
                String resId = JsonHelper.getString(res, "id");
                String resType = JsonHelper.getString(res, "resourceType");
                String fullUrl = (url != null ? url.replaceAll("\\?.*$", "") : "urn:uuid:") + "/" + (resType != null ? resType : "Resource") + "/" + (resId != null ? resId : UUID.randomUUID());

                entries.add("    {\n" +
                        "      \"fullUrl\": \"" + JsonHelper.escape(fullUrl) + "\",\n" +
                        "      \"resource\": " + indent(res, 6) + "\n" +
                        "    }");
            }
        }
        sb.append(String.join(",\n", entries));
        sb.append("\n  ]\n");
        sb.append("}");

        return sb.toString();
    }

    private static String indent(String json, int spaces) {
        if (json == null) return "{}";
        String pad = " ".repeat(spaces);
        return json.replace("\n", "\n" + pad);
    }
}
