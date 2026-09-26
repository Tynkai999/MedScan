package com.medscan.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.medscan.api.v1.HealthResource;
import com.medscan.api.v1.HealthResponse;
import com.medscan.api.v1.auth.AuthResource;
import com.medscan.api.v1.auth.LoginRequest;
import com.medscan.api.v1.auth.LoginResponse;
import com.medscan.api.v1.auth.RefreshTokenRequest;
import com.medscan.api.v1.auth.UserProfileResponse;
import com.medscan.api.v1.portal.ActorPortalResource;
import com.medscan.api.v1.portal.ActorPortalResponse;
import com.medscan.clinical.AuditEvent;
import com.medscan.clinical.BreakGlassRecord;
import com.medscan.clinical.ClinicalJsonMapper;
import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Consultation;
import com.medscan.clinical.Patient;
import com.medscan.clinical.Prescription;
import com.medscan.clinical.VitalSigns;
import com.medscan.imaging.AiAnalysisResult;
import com.medscan.imaging.ImagingJsonMapper;
import com.medscan.imaging.ImagingService;
import com.medscan.imaging.ImagingStudy;
import com.medscan.imaging.RadiologistReport;
import com.medscan.security.MedscanPrincipal;
import com.medscan.security.MedscanSecurityContext;
import com.medscan.security.jwt.InvalidTokenException;
import com.medscan.security.jwt.JsonHelper;
import com.medscan.security.jwt.JwtClaims;
import com.medscan.security.jwt.JwtTokenService;
import com.medscan.security.jwt.KeyPairProvider;
import com.medscan.security.jwt.TokenExpiredException;
import com.medscan.security.service.ActorAccount;
import com.medscan.security.service.AuthResult;
import com.medscan.security.service.AuthService;
import com.medscan.security.service.AuthenticationFailedException;
import com.medscan.security.service.SeedUserRegistry;
import com.medscan.security.tenant.TenantContext;
import com.medscan.security.tenant.VerifiedTenantIdentity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.core.Response;

/**
 * Embedded HTTP server enabling immediate testing with Postman or browser.
 * In Eclipse, right-click on this file and choose: 'Run As -> Java Application'.
 */
public class MedscanServer {

    public static final int DEFAULT_PORT = 8080;
    public static final String CONTEXT_PATH = "/medscan/api";

    private int port;
    private HttpServer server;
    private final SeedUserRegistry userRegistry;
    private final JwtTokenService tokenService;
    private final AuthService authService;
    private final HealthResource healthResource;
    private final ActorPortalResource portalResource;
    private final ClinicalService clinicalService;
    private final ImagingService imagingService;

    public MedscanServer(int port) {
        this.port = port;
        this.userRegistry = new SeedUserRegistry();
        this.tokenService = new JwtTokenService(new KeyPairProvider());
        this.authService = new AuthService(userRegistry, tokenService);
        this.healthResource = new HealthResource();
        this.portalResource = new ActorPortalResource(new TenantContext());
        this.clinicalService = new ClinicalService();
        this.imagingService = new ImagingService(clinicalService);
    }

    public ClinicalService getClinicalService() {
        return clinicalService;
    }

    public ImagingService getImagingService() {
        return imagingService;
    }

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        String portProperty = System.getProperty("medscan.port", System.getenv("PORT"));
        if (portProperty != null && !portProperty.isBlank()) {
            try {
                port = Integer.parseInt(portProperty.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        MedscanServer medscanServer = new MedscanServer(port);
        medscanServer.start();
        int activePort = medscanServer.getPort();

        System.out.println("================================================================================");
        System.out.println("  MEDSCAN ENTERPRISE — SERVEUR DE DÉVELOPPEMENT DÉMARRÉ AVEC SUCCÈS !");
        System.out.println("================================================================================");
        System.out.println("  Base URL        : http://localhost:" + activePort + CONTEXT_PATH);
        System.out.println("  Health Check    : GET  http://localhost:" + activePort + CONTEXT_PATH + "/v1/health");
        System.out.println("  Login (Auth)    : POST http://localhost:" + activePort + CONTEXT_PATH + "/v1/auth/login");
        System.out.println("  Profil (/me)    : GET  http://localhost:" + activePort + CONTEXT_PATH + "/v1/auth/me");
        System.out.println("  Portail Médecin : GET  http://localhost:" + activePort + CONTEXT_PATH + "/v1/portal/doctor");
        System.out.println("  Portail Patient : GET  http://localhost:" + activePort + CONTEXT_PATH + "/v1/portal/patient");
        System.out.println("================================================================================");
        System.out.println("  Tolérance URLs  : Accepte /v1/..., /medscan/api/v1/... et les doublons Postman");
        System.out.println("================================================================================");
        System.out.println("  Prêt pour les tests POSTMAN ! (Appuyez sur Stop dans Eclipse pour arrêter)");
        System.out.println("================================================================================");
    }

    public void start() throws IOException {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (java.net.BindException e) {
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("  [AVERTISSEMENT] Le port " + port + " est déjà occupé par un ancien processus !");
            System.err.println("  (Si une ancienne application Spring Boot tourne encore dans Eclipse,");
            System.err.println("   arrêtez-la via le carré ROUGE 'Terminate' dans la vue Console).");
            System.err.println("  Bascule automatique du serveur MedScan sur le port alternatif " + (port + 5) + "...");
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            port = port + 5;
            server = HttpServer.create(new InetSocketAddress(port), 0);
        }
        server.createContext("/", new RootDispatcher());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return port;
    }

    private class RootDispatcher implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Apply CORS headers to all responses
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String fullPath = exchange.getRequestURI().getPath();
            String subPath = normalizePath(fullPath);

            try {
                if (subPath.equals("/v1/health") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleHealth(exchange);
                } else if (subPath.equals("/v1/auth/login") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleLogin(exchange);
                } else if (subPath.equals("/v1/auth/refresh") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleRefresh(exchange);
                } else if (subPath.equals("/v1/auth/me") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleMe(exchange);
                } else if (subPath.startsWith("/v1/portal/") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handlePortal(exchange, subPath.substring("/v1/portal/".length()));
                } else if (subPath.equals("/v1/patients") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleSearchPatients(exchange);
                } else if (subPath.equals("/v1/patients") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreatePatient(exchange);
                } else if (subPath.equals("/v1/patient/my-record") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleMyRecord(exchange);
                } else if (subPath.startsWith("/v1/patients/")) {
                    handlePatientAction(exchange, subPath.substring("/v1/patients/".length()));
                } else if (subPath.startsWith("/v1/prescriptions/")) {
                    handlePrescriptionAction(exchange, subPath.substring("/v1/prescriptions/".length()));
                } else if (subPath.equals("/v1/audit/logs") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleAuditLogs(exchange);
                } else if (subPath.equals("/v1/imaging/studies") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleSearchImagingStudies(exchange);
                } else if (subPath.equals("/v1/imaging/studies") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateImagingStudy(exchange);
                } else if (subPath.startsWith("/v1/imaging/studies/")) {
                    handleImagingStudyAction(exchange, subPath.substring("/v1/imaging/studies/".length()));
                } else {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Point d'accès introuvable: " + subPath + "\"}");
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"type\":\"https://medscan.org/errors/internal\",\"title\":\"Erreur interne\",\"status\":500,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private String normalizePath(String path) {
            if (path == null) {
                return "/";
            }
            String normalized = path.trim();
            while (normalized.startsWith(CONTEXT_PATH)) {
                normalized = normalized.substring(CONTEXT_PATH.length());
            }
            while (normalized.startsWith("/medscan")) {
                normalized = normalized.substring("/medscan".length());
            }
            while (normalized.startsWith("/api")) {
                normalized = normalized.substring("/api".length());
            }
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            return normalized;
        }

        private void handleHealth(HttpExchange exchange) throws IOException {
            HealthResponse response = healthResource.getHealth();
            sendJson(exchange, 200, "{\"status\":\"" + response.status() + "\"}");
        }

        private void handleLogin(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String username = JsonHelper.getString(body, "username");
            String password = JsonHelper.getString(body, "password");

            if (username == null || password == null) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"Nom d'utilisateur et mot de passe requis.\"}");
                return;
            }

            try {
                AuthResult result = authService.login(username, password);
                ActorAccount account = result.account();

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"accessToken\":\"").append(result.accessToken()).append("\",");
                json.append("\"refreshToken\":\"").append(result.refreshToken()).append("\",");
                json.append("\"tokenType\":\"Bearer\",");
                json.append("\"expiresIn\":").append(result.expiresInSeconds()).append(",");
                json.append("\"user\":{");
                json.append("\"id\":\"").append(account.userId()).append("\",");
                json.append("\"username\":\"").append(JsonHelper.escape(account.username())).append("\",");
                json.append("\"email\":\"").append(JsonHelper.escape(account.email())).append("\",");
                json.append("\"displayName\":\"").append(JsonHelper.escape(account.displayName())).append("\",");
                json.append("\"tenantId\":\"").append(account.tenantId()).append("\",");
                json.append("\"tenantCode\":\"").append(JsonHelper.escape(account.tenantCode())).append("\",");
                json.append("\"roles\":[")
                        .append(account.roles().stream().map(r -> "\"" + JsonHelper.escape(r) + "\"").collect(Collectors.joining(",")))
                        .append("],");
                json.append("\"permissions\":[")
                        .append(account.permissions().stream().map(p -> "\"" + JsonHelper.escape(p) + "\"").collect(Collectors.joining(",")))
                        .append("]");
                json.append("}");
                json.append("}");

                sendJson(exchange, 200, json.toString());
            } catch (AuthenticationFailedException e) {
                sendJson(exchange, 401, "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Échec d'authentification\",\"status\":401,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleRefresh(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String refreshToken = JsonHelper.getString(body, "refreshToken");

            if (refreshToken == null || refreshToken.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"Le jeton de rafraîchissement est requis.\"}");
                return;
            }

            try {
                AuthResult result = authService.refreshToken(refreshToken);
                ActorAccount account = result.account();

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"accessToken\":\"").append(result.accessToken()).append("\",");
                json.append("\"refreshToken\":\"").append(result.refreshToken()).append("\",");
                json.append("\"tokenType\":\"Bearer\",");
                json.append("\"expiresIn\":").append(result.expiresInSeconds()).append(",");
                json.append("\"user\":{");
                json.append("\"id\":\"").append(account.userId()).append("\",");
                json.append("\"username\":\"").append(JsonHelper.escape(account.username())).append("\",");
                json.append("\"email\":\"").append(JsonHelper.escape(account.email())).append("\",");
                json.append("\"displayName\":\"").append(JsonHelper.escape(account.displayName())).append("\",");
                json.append("\"tenantId\":\"").append(account.tenantId()).append("\",");
                json.append("\"tenantCode\":\"").append(JsonHelper.escape(account.tenantCode())).append("\"");
                json.append("}}");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJson(exchange, 401, "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Jeton invalide\",\"status\":401,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleMe(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) {
                return; // 401 already sent
            }

            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();
            ActorAccount account = userRegistry.findById(principal.getUserId())
                    .orElse(new ActorAccount(
                            principal.getUserId(),
                            principal.getName(),
                            principal.getName(),
                            "***",
                            principal.getTenantId(),
                            "TENANT",
                            principal.getName(),
                            principal.getRoles(),
                            principal.getPermissions()));

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"id\":\"").append(account.userId()).append("\",");
            json.append("\"username\":\"").append(JsonHelper.escape(account.username())).append("\",");
            json.append("\"email\":\"").append(JsonHelper.escape(account.email())).append("\",");
            json.append("\"displayName\":\"").append(JsonHelper.escape(account.displayName())).append("\",");
            json.append("\"tenantId\":\"").append(account.tenantId()).append("\",");
            json.append("\"tenantCode\":\"").append(JsonHelper.escape(account.tenantCode())).append("\",");
            json.append("\"roles\":[")
                    .append(account.roles().stream().map(r -> "\"" + JsonHelper.escape(r) + "\"").collect(Collectors.joining(",")))
                    .append("],");
            json.append("\"permissions\":[")
                    .append(account.permissions().stream().map(p -> "\"" + JsonHelper.escape(p) + "\"").collect(Collectors.joining(",")))
                    .append("]");
            json.append("}");

            sendJson(exchange, 200, json.toString());
        }

        private void handlePortal(HttpExchange exchange, String portalPath) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) {
                return; // 401 already sent
            }

            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            // Determine required role based on portal path
            String requiredRole;
            String portalName;
            String message;

            switch (portalPath.toLowerCase()) {
                case "patient" -> {
                    requiredRole = "PATIENT";
                    portalName = "Espace Patient";
                    message = "Bienvenue sur votre carnet de santé numérique MedScan.";
                }
                case "doctor" -> {
                    requiredRole = "DOCTOR";
                    portalName = "Portail Médecin";
                    message = "Accès autorisé au dossier clinique et aux consultations.";
                }
                case "radiologist" -> {
                    requiredRole = "RADIOLOGIST";
                    portalName = "Portail Radiologie & Imagerie";
                    message = "Accès autorisé à l'imagerie médicale et aux modules d'analyse IA.";
                }
                case "nurse" -> {
                    requiredRole = "NURSE";
                    portalName = "Portail Soins Infirmiers";
                    message = "Accès autorisé à l'enregistrement des constantes et soins.";
                }
                case "lab-technician" -> {
                    requiredRole = "LAB_TECHNICIAN";
                    portalName = "Portail Laboratoire d'Analyses";
                    message = "Accès autorisé aux résultats d'examens biologiques.";
                }
                case "pharmacist" -> {
                    requiredRole = "PHARMACIST";
                    portalName = "Portail Pharmacie d'Officine";
                    message = "Accès autorisé au catalogue, stocks et ordonnances.";
                }
                case "delivery" -> {
                    requiredRole = "DELIVERY_AGENT";
                    portalName = "Portail Livraison & Logistique";
                    message = "Accès autorisé aux missions d'acheminement pharmaceutique.";
                }
                case "tenant-admin" -> {
                    requiredRole = "TENANT_ADMIN";
                    portalName = "Administration Établissement";
                    message = "Accès autorisé à la gestion des départements et du personnel.";
                }
                case "super-admin" -> {
                    requiredRole = "SUPER_ADMIN";
                    portalName = "Super-Administration MedScan";
                    message = "Accès autorisé à la gouvernance globale de la plateforme.";
                }
                case "auditor" -> {
                    requiredRole = "AUDITOR";
                    portalName = "Portail Audit & Conformité DPO";
                    message = "Accès autorisé aux pistes d'audit et rapports de sécurité.";
                }
                default -> {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Portail inconnu\",\"status\":404,\"detail\":\"Portail inconnu: " + portalPath + "\"}");
                    return;
                }
            }

            boolean isAuthorized = context.isUserInRole(requiredRole) || context.isUserInRole("SUPER_ADMIN");
            if (!isAuthorized) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Vos habilitations ne vous permettent pas d'accéder au " + portalName + ". Rôle requis: " + requiredRole + "\"}");
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"portalName\":\"").append(JsonHelper.escape(portalName)).append("\",");
            json.append("\"authorizedRole\":\"").append(requiredRole).append("\",");
            json.append("\"userId\":\"").append(principal.getUserId()).append("\",");
            json.append("\"username\":\"").append(JsonHelper.escape(principal.getName())).append("\",");
            json.append("\"tenantId\":\"").append(principal.getTenantId()).append("\",");
            json.append("\"message\":\"").append(JsonHelper.escape(message)).append("\"");
            json.append("}");

            sendJson(exchange, 200, json.toString());
        }

        private void handleSearchPatients(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("DOCTOR") && !principal.isUserInRole("NURSE")
                    && !principal.isUserInRole("TENANT_ADMIN") && !principal.isUserInRole("SUPER_ADMIN")
                    && !principal.isUserInRole("AUDITOR")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Vos habilitations ne permettent pas de rechercher des patients.\"}");
                return;
            }

            String query = null;
            String rawQuery = exchange.getRequestURI().getQuery();
            if (rawQuery != null) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && ("q".equalsIgnoreCase(kv[0]) || "query".equalsIgnoreCase(kv[0]))) {
                        query = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                }
            }

            List<Patient> list = clinicalService.searchPatients(principal.getTenantId(), query, principal.getRoles());
            sendJson(exchange, 200, ClinicalJsonMapper.toPatientListJson(list));
        }

        private void handleCreatePatient(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("DOCTOR") && !principal.isUserInRole("NURSE")
                    && !principal.isUserInRole("TENANT_ADMIN") && !principal.isUserInRole("SUPER_ADMIN")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Vos habilitations ne permettent pas de créer un dossier patient.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Patient patient = ClinicalJsonMapper.parsePatient(body, principal.getTenantId());
            clinicalService.createPatient(patient, principal.getUserId(), principal.getName(),
                    principal.getRoles().iterator().next(), principal.getTenantId());

            sendJson(exchange, 201, ClinicalJsonMapper.toJson(patient));
        }

        private void handleMyRecord(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            Optional<Patient> opt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
            if (opt.isEmpty()) {
                sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Dossier introuvable\",\"status\":404,\"detail\":\"Aucun dossier médical n'est actuellement rattaché à votre compte.\"}");
                return;
            }

            Patient patient = opt.get();
            var vitals = clinicalService.getVitalSigns(patient.id());
            var consultations = clinicalService.getConsultations(patient.id());
            var prescriptions = clinicalService.getPrescriptionsForPatient(patient.id());

            clinicalService.logAudit(principal.getUserId(), principal.getName(), "PATIENT", principal.getTenantId(),
                    "OWN_RECORD_ACCESSED", "Patient", patient.id().toString(), "SUCCESS",
                    "Le patient a consulté son propre carnet de santé numérique.");

            sendJson(exchange, 200, ClinicalJsonMapper.toDossierJson(patient, vitals, consultations, prescriptions));
        }

        private void handlePatientAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String method = exchange.getRequestMethod();

            if (!tail.contains("/")) {
                if (!"GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Seule la méthode GET est acceptée sur cette ressource.\"}");
                    return;
                }

                UUID patientId;
                try {
                    patientId = UUID.fromString(tail);
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"L'identifiant patient n'est pas un UUID valide.\"}");
                    return;
                }

                if (!clinicalService.canAccessPatient(patientId, principal.getUserId(), principal.getTenantId(), principal.getRoles())) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Cloisonnement tenant actif\",\"status\":403,\"detail\":\"Accès refusé au dossier patient d'un autre établissement. Utilisez le protocole Break-Glass si urgence vitale.\"}");
                    return;
                }

                Optional<Patient> opt = clinicalService.findPatientById(patientId);
                if (opt.isEmpty()) {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Patient introuvable\",\"status\":404,\"detail\":\"Aucun dossier trouvé pour le patient: " + patientId + "\"}");
                    return;
                }

                Patient patient = opt.get();
                var vitals = clinicalService.getVitalSigns(patientId);
                var consultations = clinicalService.getConsultations(patientId);
                var prescriptions = clinicalService.getPrescriptionsForPatient(patientId);

                clinicalService.logAudit(principal.getUserId(), principal.getName(),
                        principal.getRoles().iterator().next(), principal.getTenantId(),
                        "PATIENT_DOSSIER_ACCESSED", "Patient", patientId.toString(), "SUCCESS",
                        "Consultation du dossier médical complet de " + patient.fullName());

                sendJson(exchange, 200, ClinicalJsonMapper.toDossierJson(patient, vitals, consultations, prescriptions));
                return;
            }

            String[] parts = tail.split("/", 2);
            UUID patientId;
            try {
                patientId = UUID.fromString(parts[0]);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"UUID patient invalide.\"}");
                return;
            }

            Optional<Patient> opt = clinicalService.findPatientById(patientId);
            if (opt.isEmpty()) {
                sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Patient introuvable\",\"status\":404,\"detail\":\"Patient introuvable.\"}");
                return;
            }
            Patient patient = opt.get();

            String action = parts[1];

            if ("vitals".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("NURSE") && !principal.isUserInRole("DOCTOR")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul le personnel infirmier ou médical peut enregistrer des constantes vitales.\"}");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String role = principal.isUserInRole("DOCTOR") ? "DOCTOR" : "NURSE";
                VitalSigns vitals = ClinicalJsonMapper.parseVitalSigns(body, patientId, principal.getName(), role);
                clinicalService.recordVitals(vitals, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                sendJson(exchange, 201, ClinicalJsonMapper.toJson(vitals));
                return;
            }

            if ("consultations".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("DOCTOR")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un médecin assermenté peut rédiger des notes cliniques de consultation.\"}");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Consultation consultation = ClinicalJsonMapper.parseConsultation(body, patientId, principal.getUserId(), principal.getName(), principal.getTenantId());
                clinicalService.recordConsultation(consultation, principal.getUserId(), principal.getName(), "DOCTOR", principal.getTenantId());
                sendJson(exchange, 201, ClinicalJsonMapper.toJson(consultation));
                return;
            }

            if ("prescriptions".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("DOCTOR")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un médecin assermenté peut émettre une ordonnance médicale numérique.\"}");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Prescription rx = ClinicalJsonMapper.parsePrescription(body, patientId, patient.fullName(), principal.getUserId(), principal.getName(), principal.getTenantId());
                clinicalService.issuePrescription(rx, principal.getUserId(), principal.getName(), principal.getTenantId());
                sendJson(exchange, 201, ClinicalJsonMapper.toJson(rx));
                return;
            }

            if ("break-glass".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("DOCTOR")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un médecin peut déclencher la procédure dérogatoire d'urgence vitale (Break-Glass).\"}");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String reason = JsonHelper.getString(body, "reason");
                if (reason == null || reason.isBlank()) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Motif obligatoire\",\"status\":400,\"detail\":\"Un motif clinique explicite est légalement requis pour lever les restrictions d'accès en urgence.\"}");
                    return;
                }
                BreakGlassRecord bg = clinicalService.triggerBreakGlass(patientId, principal.getUserId(), principal.getName(), principal.getTenantId(), reason);
                sendJson(exchange, 200, "{\"status\":\"OVERRIDDEN\",\"message\":\"Accès d'urgence dérogatoire accordé pour le patient " + JsonHelper.escape(patient.fullName()) + "\",\"eventId\":\"" + bg.id() + "\",\"loggedAt\":\"" + bg.timestamp() + "\"}");
                return;
            }

            sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Action inconnue\",\"status\":404,\"detail\":\"Action introuvable: " + action + "\"}");
        }

        private void handlePrescriptionAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String method = exchange.getRequestMethod();

            if (tail.endsWith("/dispense") && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("PHARMACIST")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un pharmacien d'officine habilité peut valider la dispensation d'une ordonnance.\"}");
                    return;
                }
                String code = tail.substring(0, tail.length() - "/dispense".length());
                try {
                    Prescription dispensed = clinicalService.dispensePrescription(code, principal.getName(), principal.getTenantId(), principal.getUserId());
                    sendJson(exchange, 200, ClinicalJsonMapper.toJson(dispensed));
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Ordonnance introuvable\",\"status\":404,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                } catch (IllegalStateException e) {
                    sendJson(exchange, 409, "{\"type\":\"https://medscan.org/errors/conflict\",\"title\":\"Déjà dispensée\",\"status\":409,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                String code = tail;
                Optional<Prescription> opt = clinicalService.findPrescriptionByCode(code);
                if (opt.isEmpty()) {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Ordonnance introuvable\",\"status\":404,\"detail\":\"Aucune ordonnance trouvée pour le code: " + JsonHelper.escape(code) + "\"}");
                    return;
                }
                Prescription rx = opt.get();
                clinicalService.logAudit(principal.getUserId(), principal.getName(),
                        principal.getRoles().iterator().next(), principal.getTenantId(),
                        "PRESCRIPTION_LOOKUP", "Prescription", code, "SUCCESS",
                        "Consultation de l'ordonnance " + code + " pour " + rx.patientName());

                sendJson(exchange, 200, ClinicalJsonMapper.toJson(rx));
                return;
            }

            sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode non autorisée.\"}");
        }

        private void handleAuditLogs(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("AUDITOR") && !principal.isUserInRole("SUPER_ADMIN") && !principal.isUserInRole("TENANT_ADMIN")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul le responsable d'audit (DPO) ou l'administrateur peut consulter le journal d'audit.\"}");
                return;
            }

            boolean isPlatformAuditor = principal.isUserInRole("AUDITOR") || principal.isUserInRole("SUPER_ADMIN");
            List<AuditEvent> logs = clinicalService.getAuditLogs(principal.getTenantId(), isPlatformAuditor, 100);
            sendJson(exchange, 200, ClinicalJsonMapper.toAuditListJson(logs));
        }

        private void handleSearchImagingStudies(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("RADIOLOGIST") && !principal.isUserInRole("DOCTOR")
                    && !principal.isUserInRole("TENANT_ADMIN") && !principal.isUserInRole("SUPER_ADMIN")
                    && !principal.isUserInRole("AUDITOR")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Vos habilitations ne permettent pas de consulter les examens d'imagerie.\"}");
                return;
            }

            UUID patientId = null;
            String rawQuery = exchange.getRequestURI().getQuery();
            if (rawQuery != null) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "patientId".equalsIgnoreCase(kv[0])) {
                        try { patientId = UUID.fromString(kv[1]); } catch (Exception ignored) {}
                    }
                }
            }

            List<ImagingStudy> list = imagingService.searchStudies(principal.getTenantId(), patientId, principal.getRoles());
            sendJson(exchange, 200, ImagingJsonMapper.toStudyListJson(list));
        }

        private void handleCreateImagingStudy(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("RADIOLOGIST") && !principal.isUserInRole("DOCTOR") && !principal.isUserInRole("TENANT_ADMIN")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul le personnel d'imagerie ou médical peut créer une étude radiologique.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            ImagingStudy study = ImagingJsonMapper.parseStudy(body, principal.getTenantId(), principal.getUserId(), principal.getName());
            imagingService.createStudy(study, principal.getUserId(), principal.getName(), principal.getRoles().iterator().next(), principal.getTenantId());
            sendJson(exchange, 201, ImagingJsonMapper.toJson(study));
        }

        private void handleImagingStudyAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();
            String method = exchange.getRequestMethod();

            if (!tail.contains("/")) {
                if (!"GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode non autorisée.\"}");
                    return;
                }
                UUID studyId;
                try {
                    studyId = UUID.fromString(tail);
                } catch (Exception e) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"UUID d'examen invalide.\"}");
                    return;
                }
                Optional<ImagingStudy> opt = imagingService.findStudyById(studyId);
                if (opt.isEmpty()) {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Examen introuvable\",\"status\":404,\"detail\":\"Aucun examen radiologique trouvé pour: " + studyId + "\"}");
                    return;
                }
                sendJson(exchange, 200, ImagingJsonMapper.toJson(opt.get()));
                return;
            }

            String[] parts = tail.split("/", 2);
            UUID studyId;
            try {
                studyId = UUID.fromString(parts[0]);
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"UUID d'examen invalide.\"}");
                return;
            }

            String action = parts[1];

            if ("ai-analyze".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("RADIOLOGIST") && !principal.isUserInRole("DOCTOR")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un médecin ou un radiologue peut déclencher une inférence d'IA clinique.\"}");
                    return;
                }
                try {
                    ImagingStudy analyzed = imagingService.triggerAiAnalysis(studyId, principal.getUserId(), principal.getName(), principal.getRoles().iterator().next(), principal.getTenantId());
                    sendJson(exchange, 200, ImagingJsonMapper.toJson(analyzed));
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Examen introuvable\",\"status\":404,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
                return;
            }

            if ("report".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("RADIOLOGIST") && !principal.isUserInRole("DOCTOR")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un radiologue ou un médecin peut signer un compte-rendu radiologique officiel.\"}");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String role = principal.isUserInRole("RADIOLOGIST") ? "RADIOLOGIST" : "DOCTOR";
                RadiologistReport report = ImagingJsonMapper.parseReport(body, studyId, principal.getUserId(), principal.getName(), role);
                try {
                    ImagingStudy reported = imagingService.addRadiologistReport(studyId, report, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, ImagingJsonMapper.toJson(reported));
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Examen introuvable\",\"status\":404,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
                return;
            }

            sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Action inconnue\",\"status\":404,\"detail\":\"Action introuvable: " + action + "\"}");
        }

        private MedscanSecurityContext authenticate(HttpExchange exchange) throws IOException {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                sendJson(exchange, 401, "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Non authentifié\",\"status\":401,\"detail\":\"En-tête Authorization Bearer manquant ou invalide.\"}");
                return null;
            }

            String token = authHeader.substring(7).trim();
            try {
                JwtClaims claims = tokenService.validateAndParse(token);
                VerifiedTenantIdentity identity = new VerifiedTenantIdentity(
                        claims.tenantId(),
                        claims.subject(),
                        claims.preferredUsername(),
                        claims.roles(),
                        claims.permissions());
                MedscanPrincipal principal = new MedscanPrincipal(identity);
                return new MedscanSecurityContext(principal, false);
            } catch (TokenExpiredException e) {
                sendJson(exchange, 401, "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Jeton expiré\",\"status\":401,\"detail\":\"Le jeton d'authentification a expiré.\"}");
                return null;
            } catch (InvalidTokenException e) {
                sendJson(exchange, 401, "{\"type\":\"https://medscan.org/errors/unauthorized\",\"title\":\"Jeton invalide\",\"status\":401,\"detail\":\"Jeton d'authentification invalide ou altéré.\"}");
                return null;
            }
        }

        private void sendJson(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
