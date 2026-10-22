package com.medscan.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("GET /v1/deliveries filtre selon le rôle et masque l'OTP pour le livreur mais le révèle au patient")
    void testListDeliveriesByCourierAndPatient() throws Exception {
        // 1. Livreur liste ses courses
        String courierLogin = "{\"username\":\"delivery@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> courierLoginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(courierLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String courierToken = JsonHelper.getString(courierLoginResp.body(), "accessToken");

        HttpResponse<String> courierListResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries"))
                .header("Authorization", "Bearer " + courierToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, courierListResp.statusCode());
        String courierBody = courierListResp.body();
        assertTrue(courierBody.contains("DEL-2026-0089"));
        assertFalse(courierBody.contains("\"otpCode\":"), "Le code OTP clair ne doit JAMAIS apparaître pour le livreur !");

        // 2. Patient consulte ses livraisons (le code OTP lui est accessible)
        String patientLogin = "{\"username\":\"patient@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> patientLoginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(patientLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String patientToken = JsonHelper.getString(patientLoginResp.body(), "accessToken");

        HttpResponse<String> patientListResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries"))
                .header("Authorization", "Bearer " + patientToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, patientListResp.statusCode());
        String patientBody = patientListResp.body();
        assertTrue(patientBody.contains("DEL-2026-0089"));
        assertTrue(patientBody.contains("\"otpCode\":\"482910\""), "Le patient destinataire doit pouvoir lire son code OTP.");
    }

    @Test
    @DisplayName("Workflow complet HTTP de livraison avec validation OTP et règles de sécurité strictes")
    void testDeliveryFullOtpValidationFlow() throws Exception {
        // 1. Pharmacien crée une mission de livraison
        String pharmaLogin = "{\"username\":\"pharmacist@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> pharmaLoginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(pharmaLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String pharmaToken = JsonHelper.getString(pharmaLoginResp.body(), "accessToken");

        String createDeliveryPayload = """
                {
                    "prescriptionCode": "RX-2026-0042",
                    "deliveryAddress": "Zone du Bois, Villa 102",
                    "destinationCity": "Ouagadougou",
                    "packageDescription": "Traitement aérosol scellé",
                    "specialInstructions": "Contrôle d'identité requis"
                }
                """;
        HttpResponse<String> createResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries"))
                .header("Authorization", "Bearer " + pharmaToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createDeliveryPayload)).build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, createResp.statusCode());
        String deliveryId = JsonHelper.getString(createResp.body(), "id");
        String deliveryCode = JsonHelper.getString(createResp.body(), "deliveryCode");
        assertNotNull(deliveryId);

        // 2. Patient récupère son code OTP secret
        String patientLogin = "{\"username\":\"patient@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> patientLoginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(patientLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String patientToken = JsonHelper.getString(patientLoginResp.body(), "accessToken");

        HttpResponse<String> patientOtpResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries/" + deliveryId + "/otp"))
                .header("Authorization", "Bearer " + patientToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, patientOtpResp.statusCode());
        String secretOtp = JsonHelper.getString(patientOtpResp.body(), "otpCode");
        assertNotNull(secretOtp);
        assertEquals(6, secretOtp.length());

        // 3. Livreur tente d'accéder à l'endpoint OTP -> FORBIDDEN 403
        String courierLogin = "{\"username\":\"delivery@medscan.org\",\"password\":\"" + SeedUserRegistry.DEFAULT_PASSWORD + "\"}";
        HttpResponse<String> courierLoginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(courierLogin)).build(), HttpResponse.BodyHandlers.ofString());
        String courierToken = JsonHelper.getString(courierLoginResp.body(), "accessToken");

        HttpResponse<String> courierOtpAttempt = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries/" + deliveryId + "/otp"))
                .header("Authorization", "Bearer " + courierToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, courierOtpAttempt.statusCode());

        // 4. Livreur s'assigne la mission
        HttpResponse<String> assignResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries/" + deliveryId + "/assign"))
                .header("Authorization", "Bearer " + courierToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, assignResp.statusCode());
        assertTrue(assignResp.body().contains("\"status\":\"ASSIGNED\""));

        // 5. Livreur retire le colis scellé en pharmacie
        HttpResponse<String> pickupResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries/" + deliveryId + "/pickup"))
                .header("Authorization", "Bearer " + courierToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pickupResp.statusCode());
        assertTrue(pickupResp.body().contains("\"status\":\"IN_TRANSIT\""));

        // 6. Livreur teste un mauvais OTP -> 400 Bad Request
        HttpResponse<String> badOtpResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries/" + deliveryId + "/complete"))
                .header("Authorization", "Bearer " + courierToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"otp\":\"999999\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, badOtpResp.statusCode());
        assertTrue(badOtpResp.body().contains("incorrect"));

        // 7. Livreur soumet le bon OTP fourni par le patient -> 200 DELIVERED !
        HttpResponse<String> goodOtpResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/deliveries/" + deliveryId + "/complete"))
                .header("Authorization", "Bearer " + courierToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"otp\":\"" + secretOtp + "\",\"recipientConfirmation\":\"Mme Fatou Ouedraogo\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, goodOtpResp.statusCode());
        assertTrue(goodOtpResp.body().contains("\"status\":\"DELIVERED\""));
        assertTrue(goodOtpResp.body().contains("Mme Fatou Ouedraogo"));
    }

    // ==========================================
    // TESTS INTÉGRATION SYNCHRONISATION HORS-LIGNE (MOD-10)
    // ==========================================

    private static String login(String username, String password) throws Exception {
        HttpResponse<String> loginResp = client.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        return JsonHelper.getString(loginResp.body(), "accessToken");
    }

    @Test
    @DisplayName("HTTP GET /v1/sync/vault permet de télécharger le coffre hors-ligne complet")
    void testSyncPullVaultEndpoint() throws Exception {
        String token = login("doctor@medscan.org", "Doctor123!");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/sync/vault"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"patients\":"));
        assertTrue(response.body().contains("\"vitalSigns\":"));
        assertTrue(response.body().contains("\"consultations\":"));
        assertTrue(response.body().contains("\"emergencyCards\":"));
        assertTrue(response.body().contains("\"syncTimestamp\":"));
    }

    @Test
    @DisplayName("HTTP GET /v1/sync/emergency-card/{patientId} génère la fiche d'urgence avec empreinte SHA-256")
    void testSyncEmergencyCardEndpoint() throws Exception {
        String token = login("doctor@medscan.org", "Doctor123!");
        String patientId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/sync/emergency-card/" + patientId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"fullName\":\"Fatou Ouedraogo\""));
        assertTrue(response.body().contains("\"bloodGroup\":\"A+\""));
        assertTrue(response.body().contains("\"integrityHash\":"));
    }

    @Test
    @DisplayName("HTTP POST /v1/sync/push traite les lots hors-ligne avec déduplication idempotente stricte")
    void testSyncPushIdempotencyEndpoint() throws Exception {
        String token = login("doctor@medscan.org", "Doctor123!");
        String opId = java.util.UUID.randomUUID().toString();
        String batchId = java.util.UUID.randomUUID().toString();
        String patientId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";

        String batchBody = "{\"batchId\":\"" + batchId + "\",\"deviceId\":\"tablet-burkina-01\",\"operations\":[{\"operationId\":\"" + opId + "\",\"entityType\":\"VITAL_SIGNS\",\"action\":\"CREATE\",\"occurredAt\":\"2026-10-22T20:00:00Z\",\"payload\":{\"patientId\":\"" + patientId + "\",\"systolicBp\":122,\"diastolicBp\":80,\"heartRate\":75,\"temperature\":37.0,\"notes\":\"Mission mobile brousse\"}}]}";

        // 1er envoi : opération appliquée
        HttpRequest push1 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/sync/push"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(batchBody))
                .build();

        HttpResponse<String> resp1 = client.send(push1, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp1.statusCode());
        assertTrue(resp1.body().contains("\"appliedCount\":1"));
        assertTrue(resp1.body().contains("\"duplicateCount\":0"));
        assertTrue(resp1.body().contains("\"status\":\"APPLIED\""));

        // 2e envoi identique (rejeu réseau après micro-coupure 2G/3G) : dédupliqué sans erreur
        HttpResponse<String> resp2 = client.send(push1, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp2.statusCode());
        assertTrue(resp2.body().contains("\"appliedCount\":0"));
        assertTrue(resp2.body().contains("\"duplicateCount\":1"));
        assertTrue(resp2.body().contains("\"status\":\"DUPLICATE_IGNORED\""));
    }

    // ==========================================
    // TESTS INTÉGRATION FHIR R4 (MOD-07)
    // ==========================================

    @Test
    @DisplayName("HTTP GET /fhir/r4/metadata renvoie 200 CapabilityStatement publiquement sans auth")
    void testFhirMetadataEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fhir/r4/metadata"))
                .header("Accept", "application/fhir+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/fhir+json"));
        assertTrue(response.body().contains("\"resourceType\":\"CapabilityStatement\"") || response.body().contains("\"resourceType\": \"CapabilityStatement\""));
        assertTrue(response.body().contains("\"fhirVersion\":\"4.0.1\"") || response.body().contains("\"fhirVersion\": \"4.0.1\""));
    }

    @Test
    @DisplayName("HTTP GET /fhir/r4/Patient/{id} renvoie la ressource Patient FHIR avec authentification")
    void testFhirPatientGetEndpoint() throws Exception {
        String token = login("doctor@medscan.org", "Doctor123!");
        String patientId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fhir/r4/Patient/" + patientId))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/fhir+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"resourceType\": \"Patient\"") || response.body().contains("\"resourceType\":\"Patient\""));
        assertTrue(response.body().contains("Fatou"));
        assertTrue(response.body().contains("Ouedraogo"));
        assertTrue(response.body().contains("BFA-2026-008412"));
    }

    @Test
    @DisplayName("HTTP GET /fhir/r4/Patient/{id} sans jeton renvoie 401 Unauthorized")
    void testFhirPatientUnauthorized() throws Exception {
        String patientId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fhir/r4/Patient/" + patientId))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("HTTP GET /fhir/r4/Observation renvoie les constantes vitales codifiées LOINC")
    void testFhirObservationsSearchEndpoint() throws Exception {
        String token = login("doctor@medscan.org", "Doctor123!");
        String patientId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fhir/r4/Observation?patient=" + patientId))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/fhir+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"resourceType\": \"Bundle\"") || response.body().contains("\"resourceType\":\"Bundle\""));
        assertTrue(response.body().contains("85354-9") || response.body().contains("8867-4"));
    }

    @Test
    @DisplayName("HTTP GET /fhir/r4/MedicationRequest/{id} renvoie la prescription codifiée FHIR")
    void testFhirMedicationRequestGetEndpoint() throws Exception {
        String token = login("doctor@medscan.org", "Doctor123!");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fhir/r4/MedicationRequest/RX-2026-0042"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/fhir+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"resourceType\": \"MedicationRequest\"") || response.body().contains("\"resourceType\":\"MedicationRequest\""));
        assertTrue(response.body().contains("RX-2026-0042"));
    }

    @Test
    @DisplayName("HTTP GET /fhir/r4/Bundle?patient={id} exporte le dossier complet en Collection Bundle")
    void testFhirBundlePatientExportEndpoint() throws Exception {
        String token = login("doctor@medscan.org", "Doctor123!");
        String patientId = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fhir/r4/Bundle?patient=" + patientId))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/fhir+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"resourceType\": \"Bundle\"") || response.body().contains("\"resourceType\":\"Bundle\""));
        assertTrue(response.body().contains("\"type\": \"collection\"") || response.body().contains("\"type\":\"collection\""));
        assertTrue(response.body().contains("Patient"));
        assertTrue(response.body().contains("Observation"));
    }
}
