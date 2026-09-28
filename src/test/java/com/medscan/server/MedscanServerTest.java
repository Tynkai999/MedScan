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

    @Test
    @DisplayName("PUT /v1/patients/{id} permet au médecin/infirmier de mettre à jour le patient avec calcul d'IMC et champs personnalisés")
    void testPatientUpdateEndpoint() throws Exception {
        String docLogin = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonHelper.getString(loginResp.body(), "accessToken");

        String fatouId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";
        String updatePayload = """
                {
                    "weightKg": 68.0,
                    "heightCm": 170.0,
                    "customFields": {
                        "circonferenceTaille": "78 cm",
                        "statutProfessionnel": "Ingénieur Télécom"
                    }
                }
                """;

        HttpResponse<String> updateResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/patients/" + fatouId))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updatePayload)).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, updateResp.statusCode());
        String body = updateResp.body();
        // 68 / (1.70^2) = 23.5
        assertTrue(body.contains("\"bmi\":23.5"));
        assertTrue(body.contains("\"bmiCategory\":\"Corpulence normale\""));
        assertTrue(body.contains("\"circonferenceTaille\":\"78 cm\""));
        assertTrue(body.contains("\"statutProfessionnel\":\"Ingénieur Télécom\""));
    }

    @Test
    @DisplayName("POST /v1/patients/{id}/consultations calcule l'IMC et met à jour le dossier maître du patient")
    void testConsultationCreationCalculatesBmiAndSyncsMasterRecord() throws Exception {
        String docLogin = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonHelper.getString(loginResp.body(), "accessToken");

        String fatouId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";
        String consultPayload = """
                {
                    "chiefComplaint": "Examen de suivi pondéral et tensionnel",
                    "examinationNotes": "Bon état général, constantes stables",
                    "diagnosis": "Profil anthropométrique optimal",
                    "treatmentPlan": "Maintenir alimentation équilibrée",
                    "weightKg": 72.0,
                    "heightCm": 170.0,
                    "systolicBp": 122,
                    "diastolicBp": 80,
                    "heartRate": 68,
                    "temperature": 36.9,
                    "oxygenSaturation": 99.0,
                    "customFields": {
                        "activiteCardio": "45 min marche quotidienne",
                        "suiviTensionnelDomicile": "Oui"
                    }
                }
                """;

        HttpResponse<String> consultResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/patients/" + fatouId + "/consultations"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(consultPayload)).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, consultResp.statusCode());
        String body = consultResp.body();
        // 72 / (1.70^2) = 24.9
        assertTrue(body.contains("\"bmi\":24.9"));
        assertTrue(body.contains("\"bmiCategory\":\"Corpulence normale\""));
        assertTrue(body.contains("\"activiteCardio\":\"45 min marche quotidienne\""));

        // Vérifier que le dossier du patient reflète les nouvelles constantes et les customFields
        HttpResponse<String> dossierResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/patients/" + fatouId))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, dossierResp.statusCode());
        String dossierBody = dossierResp.body();
        assertTrue(dossierBody.contains("\"weightKg\":72.0"));
        assertTrue(dossierBody.contains("\"bmi\":24.9"));
        assertTrue(dossierBody.contains("\"activiteCardio\":\"45 min marche quotidienne\""));
    }

    @Test
    @DisplayName("GET /v1/tenants liste les structures de santé enregistrées")
    void testListTenantsEndpoint() throws Exception {
        String docLogin = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonHelper.getString(loginResp.body(), "accessToken");

        HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/tenants"))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        String body = resp.body();
        assertTrue(body.contains("CH_OUAGADOUGOU"));
        assertTrue(body.contains("PHARMA_CENTRALE"));
        assertTrue(body.contains("LAB_BIO_SANTE"));
    }

    @Test
    @DisplayName("POST /v1/tenants permet au Super-Admin d'ajouter une nouvelle structure de santé")
    void testCreateTenantBySuperAdmin() throws Exception {
        String adminLogin = "{\"username\":\"superadmin@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(adminLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonHelper.getString(loginResp.body(), "accessToken");

        String newTenantPayload = """
                {
                    "code": "CLINIQUE_SAINT_CAMILLE",
                    "name": "Clinique Saint Camille de Ouagadougou",
                    "type": "CLINIC",
                    "city": "Ouagadougou",
                    "country": "Burkina Faso",
                    "phone": "+226 25 36 30 00",
                    "email": "contact@saint-camille.bf"
                }
                """;

        HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/tenants"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(newTenantPayload)).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, resp.statusCode());
        String body = resp.body();
        assertTrue(body.contains("\"code\":\"CLINIQUE_SAINT_CAMILLE\""));
        assertTrue(body.contains("\"type\":\"CLINIC\""));

        // Vérification de la consultation de la nouvelle structure
        String newId = JsonHelper.getString(body, "id");
        HttpResponse<String> getResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/tenants/" + newId))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, getResp.statusCode());
        assertTrue(getResp.body().contains("Clinique Saint Camille de Ouagadougou"));
    }

    @Test
    @DisplayName("POST /v1/tenants interdit l'ajout d'une structure à un médecin (403 Forbidden)")
    void testCreateTenantForbiddenForDoctor() throws Exception {
        String docLogin = "{\"username\":\"doctor@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String token = JsonHelper.getString(loginResp.body(), "accessToken");

        String payload = "{\"name\":\"Clinique Non Autorisée\",\"code\":\"CLINIC_FRAUD\"}";
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/tenants"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode());
    }

    @Test
    @DisplayName("POST /v1/users permet au Tenant-Admin d'ajouter un médecin qui peut immédiatement se connecter")
    void testCreateDoctorByTenantAdminAndImmediateLogin() throws Exception {
        // 1. Connexion Tenant Admin (Admin de l'Hôpital CH_OUAGADOUGOU)
        String adminLogin = "{\"username\":\"tenantadmin@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(adminLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String adminToken = JsonHelper.getString(loginResp.body(), "accessToken");

        // 2. Le Tenant Admin inscrit un nouveau médecin dans son hôpital
        String newDocUsername = "dr.barro@ch-ouaga.bf";
        String createUserPayload = """
                {
                    "username": "dr.barro@ch-ouaga.bf",
                    "email": "dr.barro@ch-ouaga.bf",
                    "password": "DocPassword2026!",
                    "displayName": "Dr. Souleymane Barro (Chirurgien)",
                    "role": "DOCTOR"
                }
                """;

        HttpResponse<String> createResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/users"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createUserPayload)).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, createResp.statusCode());
        String createBody = createResp.body();
        assertTrue(createBody.contains("\"username\":\"dr.barro@ch-ouaga.bf\""));
        assertTrue(createBody.contains("\"roles\":[\"DOCTOR\"]"));
        assertTrue(createBody.contains("\"tenantCode\":\"CH_OUAGADOUGOU\""));

        // 3. Le nouveau médecin se connecte immédiatement avec ses identifiants !
        String docLogin = "{\"username\":\"" + newDocUsername + "\",\"password\":\"DocPassword2026!\"}";
        HttpResponse<String> docLoginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docLogin)).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, docLoginResp.statusCode());
        String docToken = JsonHelper.getString(docLoginResp.body(), "accessToken");
        assertNotNull(docToken);

        // 4. Le nouveau médecin accède à son portail médecin avec succès
        HttpResponse<String> portalResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/portal/doctor"))
                .header("Authorization", "Bearer " + docToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, portalResp.statusCode());
        assertTrue(portalResp.body().contains("\"authorizedRole\":\"DOCTOR\""));
    }
}
