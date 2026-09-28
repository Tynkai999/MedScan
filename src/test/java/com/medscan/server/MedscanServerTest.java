package com.medscan.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.security.jwt.JsonHelper;
import com.medscan.security.service.SeedUserRegistry;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_SERVER_TESTS", matches = "true")
class MedscanServerTest {

    private static MedscanServer server;
    private static int port = 8889;
    private static String baseUrl;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws IOException {
        server = new MedscanServer(port);
        server.start();
        baseUrl = "http://localhost:" + port + MedscanServer.CONTEXT_PATH;
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("HTTP GET /v1/health renvoie 200 et status UP")
    void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }

    @Test
    @DisplayName("HTTP POST /v1/auth/login permet l'authentification et émet des tokens JWT")
    void testLoginNominal() throws Exception {
        String jsonBody = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String body = response.body();
        String accessToken = JsonHelper.getString(body, "accessToken");
        assertNotNull(accessToken);
        assertTrue(body.contains("\"roles\":[\"DOCTOR\"]"));
    }

    @Test
    @DisplayName("HTTP POST /v1/auth/login avec mot de passe erroné renvoie 401")
    void testLoginBadPassword() throws Exception {
        String jsonBody = "{\"username\":\"doctor@medscan.org\",\"password\":\"FauxMotDePasse!\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("HTTP GET /v1/portal/doctor autorise un médecin (200) et interdit un patient (403)")
    void testRbacDoctorPortal() throws Exception {
        // 1. Obtenir token Médecin
        String docLogin = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> docResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String docToken = JsonHelper.getString(docResp.body(), "accessToken");

        // 2. Obtenir token Patient
        String patLogin = "{\"username\":\"patient@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> patResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(patLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String patToken = JsonHelper.getString(patResp.body(), "accessToken");

        // 3. Médecin accède au portail médecin -> 200 OK
        HttpResponse<String> docPortalResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/portal/doctor"))
                .header("Authorization", "Bearer " + docToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, docPortalResp.statusCode());
        assertTrue(docPortalResp.body().contains("\"authorizedRole\":\"DOCTOR\""));

        // 4. Patient tente d'accéder au portail médecin -> 403 Forbidden
        HttpResponse<String> patPortalResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/portal/doctor"))
                .header("Authorization", "Bearer " + patToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, patPortalResp.statusCode());

        // 5. Patient accède à son propre portail patient -> 200 OK
        HttpResponse<String> patientPortalResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/portal/patient"))
                .header("Authorization", "Bearer " + patToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, patientPortalResp.statusCode());
        assertTrue(patientPortalResp.body().contains("\"authorizedRole\":\"PATIENT\""));
    }

    @Test
    @DisplayName("Requête sans token sur route protégée renvoie 401 Unauthorized")
    void testProtectedEndpointWithoutToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/portal/doctor"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("GET /v1/patients/{id}/vitals renvoie le groupe sanguin, allergies, SpO2, BMI et triage")
    void testEnrichedVitalsEndpoint() throws Exception {
        String docLogin = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonHelper.getString(loginResp.body(), "accessToken");

        String fatouId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";
        HttpResponse<String> vitalsResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/patients/" + fatouId + "/vitals"))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, vitalsResp.statusCode());
        String body = vitalsResp.body();
        assertTrue(body.contains("\"bloodGroup\":\"A+\""));
        assertTrue(body.contains("\"allergies\":[\"Pénicilline\",\"Arachide\"]"));
        assertTrue(body.contains("\"triageLevel\":\"NORMAL\""));
        assertTrue(body.contains("\"oxygenSaturation\":98.5"));
        assertTrue(body.contains("\"emergencyContact\":\"Moussa Ouedraogo (+226 76 11 22 33)\""));
        assertTrue(body.contains("\"bmi\":22.1"));
    }

    @Test
    @DisplayName("GET /v1/dashboard/stats renvoie les statistiques agrégées pour le rôle connecté")
    void testDashboardStatsEndpoint() throws Exception {
        String docLogin = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonHelper.getString(loginResp.body(), "accessToken");

        HttpResponse<String> dashResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/dashboard/stats"))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, dashResp.statusCode());
        String body = dashResp.body();
        assertTrue(body.contains("\"role\":\"DOCTOR\""));
        assertTrue(body.contains("\"kpis\":"));
        assertTrue(body.contains("\"totalPatients\""));
        assertTrue(body.contains("\"criticalAlerts\""));
        assertTrue(body.contains("\"recentActivities\""));
        assertTrue(body.contains("\"chartsData\""));
    }
}
