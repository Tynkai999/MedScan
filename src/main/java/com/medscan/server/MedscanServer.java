package com.medscan.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

    public MedscanServer(int port) {
        this.port = port;
        this.userRegistry = new SeedUserRegistry();
        this.tokenService = new JwtTokenService(new KeyPairProvider());
        this.authService = new AuthService(userRegistry, tokenService);
        this.healthResource = new HealthResource();
        this.portalResource = new ActorPortalResource(new TenantContext());
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
