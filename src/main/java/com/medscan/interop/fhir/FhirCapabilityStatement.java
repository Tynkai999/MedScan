package com.medscan.interop.fhir;

import java.time.LocalDate;

/**
 * Générateur du document de conformité FHIR R4 CapabilityStatement (MOD-07).
 * Requis par la spécification HL7 pour déclarer les capacités du serveur aux systèmes tiers (OpenMRS, DHIS2, etc.).
 */
public final class FhirCapabilityStatement {

    private FhirCapabilityStatement() {}

    public static String generate(String serverBaseUrl) {
        String base = (serverBaseUrl != null && !serverBaseUrl.isBlank())
                ? serverBaseUrl.replaceAll("/+$", "")
                : "https://medscan-sluw.onrender.com/medscan/api/fhir/r4";

        return "{\n" +
                "  \"resourceType\": \"CapabilityStatement\",\n" +
                "  \"id\": \"medscan-fhir-r4\",\n" +
                "  \"url\": \"" + base + "/metadata\",\n" +
                "  \"version\": \"0.1.0-SNAPSHOT\",\n" +
                "  \"name\": \"MedScanCapabilityStatement\",\n" +
                "  \"title\": \"MedScan Enterprise FHIR R4 Interoperability Hub\",\n" +
                "  \"status\": \"active\",\n" +
                "  \"experimental\": false,\n" +
                "  \"date\": \"" + LocalDate.now() + "\",\n" +
                "  \"publisher\": \"MedScan Enterprise Healthcare Platform\",\n" +
                "  \"kind\": \"instance\",\n" +
                "  \"software\": {\n" +
                "    \"name\": \"MedScan FHIR R4 Hub\",\n" +
                "    \"version\": \"0.1.0-SNAPSHOT\"\n" +
                "  },\n" +
                "  \"implementation\": {\n" +
                "    \"description\": \"MedScan HL7 FHIR R4 Hospital Interoperability Hub (Burkina Faso / Afrique de l'Ouest)\",\n" +
                "    \"url\": \"" + base + "\"\n" +
                "  },\n" +
                "  \"fhirVersion\": \"" + FhirConstants.FHIR_VERSION + "\",\n" +
                "  \"format\": [\n" +
                "    \"application/json\",\n" +
                "    \"application/fhir+json\"\n" +
                "  ],\n" +
                "  \"rest\": [\n" +
                "    {\n" +
                "      \"mode\": \"server\",\n" +
                "      \"security\": {\n" +
                "        \"cors\": true,\n" +
                "        \"service\": [\n" +
                "          {\n" +
                "            \"coding\": [\n" +
                "              {\n" +
                "                \"system\": \"http://terminology.hl7.org/CodeSystem/restful-security-service\",\n" +
                "                \"code\": \"OAuth\",\n" +
                "                \"display\": \"OAuth2 / Bearer JWT RS256\"\n" +
                "              }\n" +
                "            ],\n" +
                "            \"text\": \"Authentification OIDC / Keycloak JWT asymétrique RS256\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      \"resource\": [\n" +
                "        {\n" +
                "          \"type\": \"Patient\",\n" +
                "          \"profile\": \"http://hl7.org/fhir/StructureDefinition/Patient\",\n" +
                "          \"interaction\": [\n" +
                "            {\"code\": \"read\"},\n" +
                "            {\"code\": \"search-type\"},\n" +
                "            {\"code\": \"create\"},\n" +
                "            {\"code\": \"update\"}\n" +
                "          ],\n" +
                "          \"searchParam\": [\n" +
                "            {\"name\": \"_id\", \"type\": \"token\", \"documentation\": \"Identifiant UUID du patient\"},\n" +
                "            {\"name\": \"identifier\", \"type\": \"token\", \"documentation\": \"Numéro national d'identification (ex: BFA-2026-008412)\"},\n" +
                "            {\"name\": \"name\", \"type\": \"string\", \"documentation\": \"Nom ou prénom du patient\"}\n" +
                "          ]\n" +
                "        },\n" +
                "        {\n" +
                "          \"type\": \"Observation\",\n" +
                "          \"profile\": \"http://hl7.org/fhir/StructureDefinition/vitalsigns\",\n" +
                "          \"interaction\": [\n" +
                "            {\"code\": \"read\"},\n" +
                "            {\"code\": \"search-type\"},\n" +
                "            {\"code\": \"create\"}\n" +
                "          ],\n" +
                "          \"searchParam\": [\n" +
                "            {\"name\": \"patient\", \"type\": \"reference\", \"documentation\": \"Référence du patient (Patient/uuid)\"},\n" +
                "            {\"name\": \"code\", \"type\": \"token\", \"documentation\": \"Code LOINC (ex: 85354-9, 8867-4, 39156-5)\"},\n" +
                "            {\"name\": \"category\", \"type\": \"token\", \"documentation\": \"vital-signs\"}\n" +
                "          ]\n" +
                "        },\n" +
                "        {\n" +
                "          \"type\": \"MedicationRequest\",\n" +
                "          \"profile\": \"http://hl7.org/fhir/StructureDefinition/MedicationRequest\",\n" +
                "          \"interaction\": [\n" +
                "            {\"code\": \"read\"},\n" +
                "            {\"code\": \"search-type\"}\n" +
                "          ],\n" +
                "          \"searchParam\": [\n" +
                "            {\"name\": \"patient\", \"type\": \"reference\", \"documentation\": \"Référence du patient\"},\n" +
                "            {\"name\": \"status\", \"type\": \"token\", \"documentation\": \"Statut: active, completed, cancelled\"}\n" +
                "          ]\n" +
                "        },\n" +
                "        {\n" +
                "          \"type\": \"Bundle\",\n" +
                "          \"interaction\": [\n" +
                "            {\"code\": \"transaction\"},\n" +
                "            {\"code\": \"batch\"}\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }
}
