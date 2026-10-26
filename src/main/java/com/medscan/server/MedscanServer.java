package com.medscan.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import com.medscan.dashboard.ActorDashboardStats;
import com.medscan.dashboard.DashboardJsonMapper;
import com.medscan.dashboard.DashboardService;
import com.medscan.delivery.DeliveryJsonMapper;
import com.medscan.delivery.DeliveryOrder;
import com.medscan.delivery.DeliveryService;
import com.medscan.delivery.DeliveryStatus;
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
import com.medscan.security.service.UserJsonMapper;
import com.medscan.security.tenant.Tenant;
import com.medscan.security.tenant.TenantContext;
import com.medscan.security.tenant.TenantJsonMapper;
import com.medscan.security.tenant.TenantRegistry;
import com.medscan.security.tenant.VerifiedTenantIdentity;
import com.medscan.interop.fhir.FhirConstants;
import com.medscan.interop.fhir.FhirService;
import com.medscan.appointment.Appointment;
import com.medscan.appointment.AppointmentJsonMapper;
import com.medscan.appointment.AppointmentService;
import com.medscan.lab.LabJsonMapper;
import com.medscan.lab.LabOrder;
import com.medscan.lab.LabResultItem;
import com.medscan.lab.LabService;
import com.medscan.epidemiology.EpidemiologicalCase;
import com.medscan.epidemiology.EpidemiologyJsonMapper;
import com.medscan.epidemiology.EpidemiologyService;
import com.medscan.messaging.ClinicalMessage;
import com.medscan.messaging.InAppNotification;
import com.medscan.messaging.MessagingJsonMapper;
import com.medscan.messaging.MessagingNotificationService;
import com.medscan.referral.MedicalReferral;
import com.medscan.referral.ReferralJsonMapper;
import com.medscan.referral.ReferralService;
import com.medscan.certificate.CertificateJsonMapper;
import com.medscan.certificate.CertificateService;
import com.medscan.certificate.HospitalSeal;
import com.medscan.certificate.MedicalCertificate;
import com.medscan.grievance.GrievanceJsonMapper;
import com.medscan.grievance.GrievanceService;
import com.medscan.grievance.PatientGrievance;
import com.medscan.sync.EmergencyCard;
import com.medscan.sync.SyncBatchRequest;
import com.medscan.sync.SyncBatchResponse;
import com.medscan.sync.SyncJsonMapper;
import com.medscan.sync.SyncPackage;
import com.medscan.sync.SyncService;
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
    private final TenantRegistry tenantRegistry;
    private final SeedUserRegistry userRegistry;
    private final JwtTokenService tokenService;
    private final AuthService authService;
    private final HealthResource healthResource;
    private final ActorPortalResource portalResource;
    private final ClinicalService clinicalService;
    private final ImagingService imagingService;
    private final DeliveryService deliveryService;
    private final DashboardService dashboardService;
    private final SyncService syncService;
    private final FhirService fhirService;
    private final AppointmentService appointmentService;
    private final LabService labService;
    private final EpidemiologyService epidemiologyService;
    private final MessagingNotificationService messagingNotificationService;
    private final ReferralService referralService;
    private final CertificateService certificateService;
    private final GrievanceService grievanceService;

    public MedscanServer(int port) {
        this.port = port;
        this.tenantRegistry = new TenantRegistry();
        this.userRegistry = new SeedUserRegistry();
        this.tokenService = new JwtTokenService(new KeyPairProvider());
        this.authService = new AuthService(userRegistry, tokenService);
        this.healthResource = new HealthResource();
        this.portalResource = new ActorPortalResource(new TenantContext());
        this.clinicalService = new ClinicalService();
        this.imagingService = new ImagingService(clinicalService);
        this.deliveryService = new DeliveryService(clinicalService, tenantRegistry);
        this.dashboardService = new DashboardService(clinicalService, imagingService, tenantRegistry, deliveryService);
        this.syncService = new SyncService(clinicalService);
        this.fhirService = new FhirService(clinicalService);
        this.appointmentService = new AppointmentService(clinicalService);
        this.labService = new LabService(clinicalService);
        this.epidemiologyService = new EpidemiologyService(clinicalService);
        this.messagingNotificationService = new MessagingNotificationService(clinicalService);
        this.referralService = new ReferralService(clinicalService, tenantRegistry, messagingNotificationService);
        this.certificateService = new CertificateService(clinicalService, tenantRegistry, messagingNotificationService);
        this.grievanceService = new GrievanceService(clinicalService, tenantRegistry, messagingNotificationService, userRegistry);
    }

    public AppointmentService getAppointmentService() {
        return appointmentService;
    }

    public LabService getLabService() {
        return labService;
    }

    public EpidemiologyService getEpidemiologyService() {
        return epidemiologyService;
    }

    public MessagingNotificationService getMessagingNotificationService() {
        return messagingNotificationService;
    }

    public ReferralService getReferralService() {
        return referralService;
    }

    public CertificateService getCertificateService() {
        return certificateService;
    }

    public GrievanceService getGrievanceService() {
        return grievanceService;
    }

    public DeliveryService getDeliveryService() {
        return deliveryService;
    }

    public TenantRegistry getTenantRegistry() {
        return tenantRegistry;
    }

    public SeedUserRegistry getUserRegistry() {
        return userRegistry;
    }

    public DashboardService getDashboardService() {
        return dashboardService;
    }

    public ClinicalService getClinicalService() {
        return clinicalService;
    }

    public ImagingService getImagingService() {
        return imagingService;
    }

    public SyncService getSyncService() {
        return syncService;
    }

    public FhirService getFhirService() {
        return fhirService;
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
        System.out.println("  MOD-16 Transfert: POST http://localhost:" + activePort + CONTEXT_PATH + "/v1/referrals");
        System.out.println("  MOD-17 Cachet   : POST http://localhost:" + activePort + CONTEXT_PATH + "/v1/certificates");
        System.out.println("  MOD-18 Avis/Plt : POST http://localhost:" + activePort + CONTEXT_PATH + "/v1/grievances");
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
                } else if ((subPath.equals("/v1/tenants") || subPath.equals("/v1/admin/tenants")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListTenants(exchange);
                } else if ((subPath.equals("/v1/tenants") || subPath.equals("/v1/admin/tenants")) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateTenant(exchange);
                } else if ((subPath.startsWith("/v1/tenants/") || subPath.startsWith("/v1/admin/tenants/")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String tail = subPath.startsWith("/v1/tenants/") ? subPath.substring("/v1/tenants/".length()) : subPath.substring("/v1/admin/tenants/".length());
                    handleGetTenant(exchange, tail);
                } else if ((subPath.equals("/v1/users") || subPath.equals("/v1/tenant/users")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListUsers(exchange);
                } else if ((subPath.equals("/v1/users") || subPath.equals("/v1/tenant/users")) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateUser(exchange);
                } else if ((subPath.startsWith("/v1/users/") || subPath.startsWith("/v1/tenant/users/")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String tail = subPath.startsWith("/v1/users/") ? subPath.substring("/v1/users/".length()) : subPath.substring("/v1/tenant/users/".length());
                    handleGetUser(exchange, tail);
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
                } else if (subPath.equals("/v1/dashboard/stats") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleDashboardStats(exchange);
                } else if ((subPath.equals("/v1/deliveries") || subPath.equals("/v1/delivery/orders")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListDeliveries(exchange);
                } else if ((subPath.equals("/v1/deliveries") || subPath.equals("/v1/delivery/orders")) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateDelivery(exchange);
                } else if (subPath.startsWith("/v1/deliveries/") || subPath.startsWith("/v1/delivery/orders/")) {
                    String tail = subPath.startsWith("/v1/deliveries/") ? subPath.substring("/v1/deliveries/".length()) : subPath.substring("/v1/delivery/orders/".length());
                    handleDeliveryAction(exchange, tail);
                } else if ((subPath.equals("/v1/sync/vault") || subPath.equals("/v1/sync/pull")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleSyncPull(exchange);
                } else if (subPath.equals("/v1/sync/push") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleSyncPush(exchange);
                } else if (subPath.startsWith("/v1/sync/emergency-card/") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleEmergencyCard(exchange, subPath.substring("/v1/sync/emergency-card/".length()));
                } else if (subPath.equals("/fhir/r4/metadata") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleFhirMetadata(exchange);
                } else if (subPath.equals("/fhir/r4/Patient") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleFhirPatientSearch(exchange);
                } else if (subPath.equals("/fhir/r4/Patient") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleFhirPatientCreate(exchange);
                } else if (subPath.startsWith("/fhir/r4/Patient/")) {
                    handleFhirPatientGet(exchange, subPath.substring("/fhir/r4/Patient/".length()));
                } else if (subPath.equals("/fhir/r4/Observation") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleFhirObservationSearch(exchange);
                } else if (subPath.equals("/fhir/r4/MedicationRequest") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleFhirMedicationRequestSearch(exchange);
                } else if (subPath.startsWith("/fhir/r4/MedicationRequest/")) {
                    handleFhirMedicationRequestGet(exchange, subPath.substring("/fhir/r4/MedicationRequest/".length()));
                } else if (subPath.equals("/fhir/r4/Bundle") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleFhirBundleGet(exchange);
                } else if (subPath.equals("/v1/appointments") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListAppointments(exchange);
                } else if (subPath.equals("/v1/appointments") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateAppointment(exchange);
                } else if (subPath.startsWith("/v1/appointments/")) {
                    handleAppointmentAction(exchange, subPath.substring("/v1/appointments/".length()));
                } else if (subPath.equals("/v1/lab/orders") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListLabOrders(exchange);
                } else if (subPath.equals("/v1/lab/orders") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateLabOrder(exchange);
                } else if (subPath.startsWith("/v1/lab/orders/")) {
                    handleLabOrderAction(exchange, subPath.substring("/v1/lab/orders/".length()));
                } else if (subPath.equals("/v1/epidemiology/cases") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListEpidemiologyCases(exchange);
                } else if (subPath.equals("/v1/epidemiology/cases") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleReportEpidemiologyCase(exchange);
                } else if (subPath.equals("/v1/epidemiology/summary") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleEpidemiologySummary(exchange);
                } else if (subPath.equals("/v1/epidemiology/dhis2-export") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleEpidemiologyDhis2Export(exchange);
                } else if ((subPath.equals("/v1/messages/conversations") || subPath.equals("/v1/conversations")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListConversations(exchange);
                } else if ((subPath.equals("/v1/messages/contacts") || subPath.equals("/v1/conversations/contacts")) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListContacts(exchange);
                } else if (subPath.equals("/v1/messages") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListMessages(exchange);
                } else if (subPath.equals("/v1/messages") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleSendMessage(exchange);
                } else if (subPath.startsWith("/v1/messages/conversation/")) {
                    handleConversationMessages(exchange, subPath.substring("/v1/messages/conversation/".length()));
                } else if (subPath.startsWith("/v1/messages/conversations/")) {
                    handleConversationMessages(exchange, subPath.substring("/v1/messages/conversations/".length()));
                } else if (subPath.equals("/v1/notifications") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListNotifications(exchange);
                } else if (subPath.equals("/v1/notifications/unread-count") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleNotificationUnreadCount(exchange);
                } else if (subPath.equals("/v1/notifications/mark-all-read") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleNotificationMarkAllRead(exchange);
                } else if (subPath.startsWith("/v1/notifications/")) {
                    handleNotificationAction(exchange, subPath.substring("/v1/notifications/".length()));
                // MOD-16 : Orientations & Transferts Médicaux
                } else if (subPath.equals("/v1/referrals") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListReferrals(exchange);
                } else if (subPath.equals("/v1/referrals") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateReferral(exchange);
                } else if (subPath.startsWith("/v1/referrals/")) {
                    handleReferralAction(exchange, subPath.substring("/v1/referrals/".length()));
                // MOD-17 : Certificats Médicaux & Cachet d'Hôpital
                } else if (subPath.equals("/v1/certificates") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListCertificates(exchange);
                } else if (subPath.equals("/v1/certificates") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleIssueCertificate(exchange);
                } else if (subPath.startsWith("/v1/certificates/verify") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleVerifyCertificate(exchange);
                } else if (subPath.startsWith("/v1/certificates/")) {
                    handleCertificateAction(exchange, subPath.substring("/v1/certificates/".length()));
                // MOD-18 : Réclamations & Avis Patients pour Direction Hospitalière
                } else if (subPath.equals("/v1/grievances") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleListGrievances(exchange);
                } else if (subPath.equals("/v1/grievances") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleSubmitGrievance(exchange);
                } else if (subPath.startsWith("/v1/grievances/")) {
                    handleGrievanceAction(exchange, subPath.substring("/v1/grievances/".length()));
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
            if (normalized.startsWith("/v1/fhir/")) {
                normalized = normalized.substring(3);
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

        // ==========================================
        // GESTION DES STRUCTURES / TENANTS (MOD-01)
        // ==========================================

        private void handleListTenants(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;

            List<Tenant> tenants = tenantRegistry.listAllTenants();
            sendJson(exchange, 200, TenantJsonMapper.toListJson(tenants));
        }

        private void handleCreateTenant(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("SUPER_ADMIN")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Privilèges insuffisants\",\"status\":403,\"detail\":\"Seul le Super-Administrateur peut créer ou rattacher des structures de santé.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String code = JsonHelper.getString(body, "code");
            String name = JsonHelper.getString(body, "name");
            String type = JsonHelper.getString(body, "type");
            String country = JsonHelper.getString(body, "country");
            String city = JsonHelper.getString(body, "city");
            String phone = JsonHelper.getString(body, "phone");
            String email = JsonHelper.getString(body, "email");
            String address = JsonHelper.getString(body, "address");

            if (name == null || name.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Champs obligatoires manquants\",\"status\":400,\"detail\":\"Le nom de la structure de santé est obligatoire.\"}");
                return;
            }

            Tenant created;
            try {
                created = tenantRegistry.createTenant(code, name, type, country, city, phone, email, address);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Données invalides\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                return;
            }

            clinicalService.logAudit(principal.getUserId(), principal.getName(), "SUPER_ADMIN", principal.getTenantId(),
                    "TENANT_CREATED", "Tenant", created.id().toString(), "SUCCESS",
                    "Création de la structure de santé: " + created.name() + " (" + created.code() + " - " + created.type() + ")");

            sendJson(exchange, 201, TenantJsonMapper.toJson(created));
        }

        private void handleGetTenant(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;

            UUID tenantId;
            try {
                tenantId = UUID.fromString(tail);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"UUID de tenant invalide.\"}");
                return;
            }

            Optional<Tenant> opt = tenantRegistry.findById(tenantId);
            if (opt.isEmpty()) {
                sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Structure introuvable\",\"status\":404,\"detail\":\"Aucune structure de santé trouvée avec l'ID: " + tenantId + "\"}");
                return;
            }

            sendJson(exchange, 200, TenantJsonMapper.toJson(opt.get()));
        }

        // ==========================================
        // GESTION DU PERSONNEL / UTILISATEURS (MOD-01 / MOD-02)
        // ==========================================

        private void handleListUsers(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("SUPER_ADMIN") && !principal.isUserInRole("TENANT_ADMIN") && !principal.isUserInRole("AUDITOR")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seuls les administrateurs et auditeurs peuvent lister les utilisateurs.\"}");
                return;
            }

            List<ActorAccount> users;
            if (principal.isUserInRole("SUPER_ADMIN") || principal.isUserInRole("AUDITOR")) {
                String filterTenant = null;
                String rawQuery = exchange.getRequestURI().getQuery();
                if (rawQuery != null) {
                    for (String param : rawQuery.split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2 && "tenantId".equalsIgnoreCase(kv[0])) {
                            filterTenant = kv[1];
                        }
                    }
                }
                if (filterTenant != null && !filterTenant.isBlank()) {
                    try {
                        users = userRegistry.findByTenantId(UUID.fromString(filterTenant));
                    } catch (Exception e) {
                        users = Collections.emptyList();
                    }
                } else {
                    users = new ArrayList<>(userRegistry.allActors());
                }
            } else {
                users = userRegistry.findByTenantId(principal.getTenantId());
            }

            sendJson(exchange, 200, UserJsonMapper.toListJson(users));
        }

        private void handleCreateUser(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("SUPER_ADMIN") && !principal.isUserInRole("TENANT_ADMIN")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un administrateur d'établissement ou le Super-Administrateur peut inscrire des utilisateurs.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String username = JsonHelper.getString(body, "username");
            String email = JsonHelper.getString(body, "email");
            String password = JsonHelper.getString(body, "password");
            String displayName = JsonHelper.getString(body, "displayName");
            String roleName = JsonHelper.getString(body, "role");
            String requestedTenantId = JsonHelper.getString(body, "tenantId");

            if (username == null || username.isBlank() || roleName == null || roleName.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Champs requis manquants\",\"status\":400,\"detail\":\"Les champs 'username' et 'role' sont obligatoires.\"}");
                return;
            }

            if (password == null || password.isBlank()) {
                password = SeedUserRegistry.DEFAULT_PASSWORD;
            }

            UUID targetTenantId;
            if (principal.isUserInRole("SUPER_ADMIN")) {
                if (requestedTenantId != null && !requestedTenantId.isBlank()) {
                    try {
                        targetTenantId = UUID.fromString(requestedTenantId);
                    } catch (IllegalArgumentException e) {
                        sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Tenant invalide\",\"status\":400,\"detail\":\"L'identifiant tenant n'est pas un UUID valide.\"}");
                        return;
                    }
                } else {
                    targetTenantId = principal.getTenantId();
                }
            } else {
                targetTenantId = principal.getTenantId();
            }

            Optional<Tenant> tenantOpt = tenantRegistry.findById(targetTenantId);
            if (tenantOpt.isEmpty()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Structure inconnue\",\"status\":400,\"detail\":\"Aucune structure de santé trouvée avec l'ID: " + targetTenantId + "\"}");
                return;
            }
            Tenant targetTenant = tenantOpt.get();

            ActorAccount created;
            try {
                created = userRegistry.createStaffUser(
                        username,
                        email,
                        password,
                        displayName,
                        roleName,
                        targetTenant.id(),
                        targetTenant.code()
                );
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur de création\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                return;
            }

            String actorRole = principal.getRoles().iterator().next();
            clinicalService.logAudit(principal.getUserId(), principal.getName(), actorRole, principal.getTenantId(),
                    "STAFF_USER_CREATED", "User", created.userId().toString(), "SUCCESS",
                    "Création du compte " + created.roles() + " : " + created.displayName() + " (" + created.username() + ") rattaché à " + targetTenant.name());

            sendJson(exchange, 201, UserJsonMapper.toJson(created));
        }

        private void handleGetUser(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            UUID userId;
            try {
                userId = UUID.fromString(tail);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"UUID utilisateur invalide.\"}");
                return;
            }

            Optional<ActorAccount> opt = userRegistry.findById(userId);
            if (opt.isEmpty()) {
                sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Utilisateur introuvable\",\"status\":404,\"detail\":\"Aucun utilisateur trouvé avec l'identifiant: " + userId + "\"}");
                return;
            }

            ActorAccount account = opt.get();
            if (!principal.isUserInRole("SUPER_ADMIN") && !principal.isUserInRole("AUDITOR") && !account.tenantId().equals(principal.getTenantId())) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Cloisonnement actif\",\"status\":403,\"detail\":\"Accès refusé au profil d'un membre d'un autre établissement.\"}");
                return;
            }

            sendJson(exchange, 200, UserJsonMapper.toJson(account));
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

                if ("GET".equalsIgnoreCase(method)) {
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

                if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                    if (!principal.isUserInRole("DOCTOR") && !principal.isUserInRole("NURSE")
                            && !principal.isUserInRole("TENANT_ADMIN") && !principal.isUserInRole("SUPER_ADMIN")) {
                        sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul le personnel soignant ou administratif autorisé peut mettre à jour un dossier patient.\"}");
                        return;
                    }
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Patient updated = ClinicalJsonMapper.parsePatientUpdate(body, patient);
                    String role = principal.getRoles().iterator().next();
                    Patient saved = clinicalService.updatePatient(patientId, updated, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, ClinicalJsonMapper.toJson(saved));
                    return;
                }

                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthodes autorisées: GET, PUT, PATCH\"}");
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

            if ("vitals".equalsIgnoreCase(action)) {
                if ("GET".equalsIgnoreCase(method)) {
                    if (!clinicalService.canAccessPatient(patientId, principal.getUserId(), principal.getTenantId(), principal.getRoles())) {
                        sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Cloisonnement tenant actif\",\"status\":403,\"detail\":\"Accès refusé au dossier patient d'un autre établissement. Utilisez le protocole Break-Glass si urgence vitale.\"}");
                        return;
                    }
                    var vitals = clinicalService.getVitalSigns(patientId);
                    sendJson(exchange, 200, ClinicalJsonMapper.toVitalsListJson(vitals));
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    if (!principal.isUserInRole("NURSE") && !principal.isUserInRole("DOCTOR")) {
                        sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul le personnel infirmier ou médical peut enregistrer des constantes vitales.\"}");
                        return;
                    }
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String role = principal.isUserInRole("DOCTOR") ? "DOCTOR" : "NURSE";
                    VitalSigns vitals = ClinicalJsonMapper.parseVitalSigns(body, patientId, principal.getName(), role);
                    VitalSigns recorded = clinicalService.recordVitals(vitals, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 201, ClinicalJsonMapper.toJson(recorded));
                    return;
                }
                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthodes autorisées: GET, POST\"}");
                return;
            }

            if ("consultations".equalsIgnoreCase(action)) {
                if ("GET".equalsIgnoreCase(method)) {
                    if (!clinicalService.canAccessPatient(patientId, principal.getUserId(), principal.getTenantId(), principal.getRoles())) {
                        sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Cloisonnement tenant actif\",\"status\":403,\"detail\":\"Accès refusé au dossier patient d'un autre établissement. Utilisez le protocole Break-Glass si urgence vitale.\"}");
                        return;
                    }
                    var consults = clinicalService.getConsultations(patientId);
                    sendJson(exchange, 200, ClinicalJsonMapper.toConsultationListJson(consults));
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    if (!principal.isUserInRole("DOCTOR")) {
                        sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un médecin assermenté peut rédiger des notes cliniques de consultation.\"}");
                        return;
                    }
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Consultation consultation = ClinicalJsonMapper.parseConsultation(body, patientId, principal.getUserId(), principal.getName(), principal.getTenantId());
                    Consultation recorded = clinicalService.recordConsultation(consultation, principal.getUserId(), principal.getName(), "DOCTOR", principal.getTenantId());
                    sendJson(exchange, 201, ClinicalJsonMapper.toJson(recorded));
                    return;
                }
                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthodes autorisées: GET, POST\"}");
                return;
            }

            if ("prescriptions".equalsIgnoreCase(action)) {
                if ("GET".equalsIgnoreCase(method)) {
                    if (!clinicalService.canAccessPatient(patientId, principal.getUserId(), principal.getTenantId(), principal.getRoles())) {
                        sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Cloisonnement tenant actif\",\"status\":403,\"detail\":\"Accès refusé au dossier patient d'un autre établissement. Utilisez le protocole Break-Glass si urgence vitale.\"}");
                        return;
                    }
                    var rxs = clinicalService.getPrescriptionsForPatient(patientId);
                    sendJson(exchange, 200, ClinicalJsonMapper.toPrescriptionListJson(rxs));
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
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
                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthodes autorisées: GET, POST\"}");
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

        private void handleDashboardStats(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String query = exchange.getRequestURI().getQuery();
            String requestedRole = null;
            if (query != null && query.contains("role=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("role=")) {
                        requestedRole = param.substring("role=".length()).trim();
                        break;
                    }
                }
            }

            String primaryRole = principal.getRoles().isEmpty() ? "DOCTOR" : principal.getRoles().iterator().next();
            String roleToUse = (requestedRole != null && !requestedRole.isBlank()) ? requestedRole : primaryRole;

            ActorDashboardStats stats = dashboardService.getDashboardStats(
                    principal.getUserId(),
                    principal.getName(),
                    roleToUse,
                    principal.getTenantId()
            );

            sendJson(exchange, 200, DashboardJsonMapper.toJson(stats));
        }

        // ==========================================
        // GESTION DES LIVRAISONS & LOGISTIQUE (MOD-09)
        // ==========================================

        private void handleListDeliveries(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            UUID patientIdFilter = null;
            if (principal.isUserInRole("PATIENT")) {
                Optional<Patient> opt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (opt.isPresent()) {
                    patientIdFilter = opt.get().id();
                }
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();
            List<DeliveryOrder> list = deliveryService.listDeliveries(principal.getUserId(), role, principal.getTenantId(), patientIdFilter);

            boolean includeOtp = principal.isUserInRole("PATIENT");
            sendJson(exchange, 200, DeliveryJsonMapper.toListJson(list, includeOtp));
        }

        private void handleCreateDelivery(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("PHARMACIST") && !principal.isUserInRole("DOCTOR")
                    && !principal.isUserInRole("PATIENT") && !principal.isUserInRole("SUPER_ADMIN")
                    && !principal.isUserInRole("TENANT_ADMIN")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Vos droits ne vous permettent pas d'ordonnancer une livraison.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String prescriptionCode = JsonHelper.getString(body, "prescriptionCode");
            String rawPatientId = JsonHelper.getString(body, "patientId");
            String patientName = JsonHelper.getString(body, "patientName");
            String patientPhone = JsonHelper.getString(body, "patientPhone");
            String deliveryAddress = JsonHelper.getString(body, "deliveryAddress");
            String destinationCity = JsonHelper.getString(body, "destinationCity");
            String rawPharmacyTenantId = JsonHelper.getString(body, "pharmacyTenantId");
            String pharmacyName = JsonHelper.getString(body, "pharmacyName");
            String packageDescription = JsonHelper.getString(body, "packageDescription");
            String specialInstructions = JsonHelper.getString(body, "specialInstructions");

            UUID patientId = null;
            UUID prescriptionId = null;
            UUID pharmacyTenantId = null;

            if (prescriptionCode != null && !prescriptionCode.isBlank()) {
                Optional<Prescription> rxOpt = clinicalService.findPrescriptionByCode(prescriptionCode.trim());
                if (rxOpt.isPresent()) {
                    Prescription rx = rxOpt.get();
                    prescriptionId = rx.id();
                    if (patientId == null) patientId = rx.patientId();
                    if (patientName == null || patientName.isBlank()) patientName = rx.patientName();
                }
            }

            if (rawPatientId != null && !rawPatientId.isBlank()) {
                try {
                    patientId = UUID.fromString(rawPatientId);
                } catch (IllegalArgumentException ignored) {}
            }

            if (patientId == null && principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) {
                    patientId = pOpt.get().id();
                    if (patientName == null || patientName.isBlank()) patientName = pOpt.get().fullName();
                    if (patientPhone == null || patientPhone.isBlank()) patientPhone = pOpt.get().phone();
                }
            }

            if (patientId == null) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"L'identifiant du patient (patientId) est requis pour créer un bon de livraison.\"}");
                return;
            }

            if (rawPharmacyTenantId != null && !rawPharmacyTenantId.isBlank()) {
                try {
                    pharmacyTenantId = UUID.fromString(rawPharmacyTenantId);
                } catch (IllegalArgumentException ignored) {}
            }
            if (pharmacyTenantId == null) {
                if (principal.isUserInRole("PHARMACIST") && principal.getTenantId() != null) {
                    pharmacyTenantId = principal.getTenantId();
                } else if (principal.getTenantId() != null) {
                    pharmacyTenantId = principal.getTenantId();
                } else {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"L'identifiant de la structure pharmaceutique (pharmacyTenantId) est requis.\"}");
                    return;
                }
            }

            String role = principal.getRoles().isEmpty() ? "PHARMACIST" : principal.getRoles().iterator().next();
            DeliveryOrder created = deliveryService.createDelivery(
                    prescriptionCode,
                    prescriptionId,
                    patientId,
                    patientName,
                    patientPhone,
                    deliveryAddress,
                    destinationCity,
                    pharmacyTenantId,
                    pharmacyName,
                    packageDescription,
                    specialInstructions,
                    principal.getUserId(),
                    principal.getName(),
                    role,
                    principal.getTenantId()
            );

            boolean includeOtp = principal.isUserInRole("PATIENT");
            sendJson(exchange, 201, DeliveryJsonMapper.toJson(created, includeOtp));
        }

        private void handleDeliveryAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String method = exchange.getRequestMethod();

            // 1. GET /v1/deliveries/{id}
            if (!tail.contains("/")) {
                Optional<DeliveryOrder> opt = findDeliveryByTail(tail);
                if (opt.isEmpty()) {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Livraison non trouvée\",\"status\":404,\"detail\":\"Aucune commande de livraison trouvée pour: " + JsonHelper.escape(tail) + "\"}");
                    return;
                }
                DeliveryOrder order = opt.get();
                if ("GET".equalsIgnoreCase(method)) {
                    boolean includeOtp = principal.isUserInRole("SUPER_ADMIN") || isPatientRecipient(principal, order);
                    sendJson(exchange, 200, DeliveryJsonMapper.toJson(order, includeOtp));
                    return;
                }
                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode autorisée: GET\"}");
                return;
            }

            int slashIdx = tail.indexOf('/');
            String idOrCode = tail.substring(0, slashIdx);
            String action = tail.substring(slashIdx + 1);

            Optional<DeliveryOrder> opt = findDeliveryByTail(idOrCode);
            if (opt.isEmpty()) {
                sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Livraison non trouvée\",\"status\":404,\"detail\":\"Aucune commande trouvée pour: " + JsonHelper.escape(idOrCode) + "\"}");
                return;
            }
            DeliveryOrder order = opt.get();

            String role = principal.getRoles().isEmpty() ? "DELIVERY_AGENT" : principal.getRoles().iterator().next();

            // 2. POST /v1/deliveries/{id}/assign
            if ("assign".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("DELIVERY_AGENT") && !principal.isUserInRole("SUPER_ADMIN") && !principal.isUserInRole("TENANT_ADMIN")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul un livreur agréé ou un superviseur peut s'assigner une course.\"}");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String rawAgentId = JsonHelper.getString(body, "deliveryAgentId");
                String agentName = JsonHelper.getString(body, "deliveryAgentName");

                UUID agentId = principal.getUserId();
                if (rawAgentId != null && !rawAgentId.isBlank() && principal.isUserInRole("SUPER_ADMIN")) {
                    try {
                        agentId = UUID.fromString(rawAgentId);
                    } catch (IllegalArgumentException ignored) {}
                }
                if (agentName == null || agentName.isBlank()) {
                    agentName = principal.getName();
                }

                try {
                    DeliveryOrder assigned = deliveryService.assignDelivery(order.id(), agentId, agentName, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, DeliveryJsonMapper.toJson(assigned, false));
                } catch (IllegalStateException e) {
                    sendJson(exchange, 409, "{\"type\":\"https://medscan.org/errors/conflict\",\"title\":\"Statut incompatible\",\"status\":409,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
                return;
            }

            // 3. POST /v1/deliveries/{id}/pickup
            if ("pickup".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("DELIVERY_AGENT") && !principal.isUserInRole("SUPER_ADMIN")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul le livreur assigné peut retirer le colis scellé.\"}");
                    return;
                }
                try {
                    DeliveryOrder pickedUp = deliveryService.pickupDelivery(order.id(), principal.getUserId(), principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, DeliveryJsonMapper.toJson(pickedUp, false));
                } catch (SecurityException e) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Livreur non assigné\",\"status\":403,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                } catch (IllegalStateException e) {
                    sendJson(exchange, 409, "{\"type\":\"https://medscan.org/errors/conflict\",\"title\":\"Statut incompatible\",\"status\":409,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
                return;
            }

            // 4. POST /v1/deliveries/{id}/complete (Vérification OTP)
            if ("complete".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
                if (!principal.isUserInRole("DELIVERY_AGENT") && !principal.isUserInRole("SUPER_ADMIN")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Seul le livreur peut soumettre le code OTP de validation de remise.\"}");
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String otp = JsonHelper.getString(body, "otp");
                if (otp == null || otp.isBlank()) {
                    otp = JsonHelper.getString(body, "otpCode");
                }
                String recipientConfirmation = JsonHelper.getString(body, "recipientConfirmation");

                if (otp == null || otp.isBlank()) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"OTP requis\",\"status\":400,\"detail\":\"Le code OTP à 6 chiffres est obligatoire pour valider la délivrance du colis.\"}");
                    return;
                }

                try {
                    DeliveryOrder completed = deliveryService.completeDeliveryWithOtp(
                            order.id(),
                            otp,
                            recipientConfirmation,
                            principal.getUserId(),
                            principal.getUserId(),
                            principal.getName(),
                            role,
                            principal.getTenantId()
                    );
                    sendJson(exchange, 200, DeliveryJsonMapper.toJson(completed, false));
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Code OTP incorrect\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                } catch (IllegalStateException e) {
                    sendJson(exchange, 409, "{\"type\":\"https://medscan.org/errors/conflict\",\"title\":\"Échec de livraison\",\"status\":409,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
                return;
            }

            // 5. GET /v1/deliveries/{id}/otp
            if ("otp".equalsIgnoreCase(action) && "GET".equalsIgnoreCase(method)) {
                if (principal.isUserInRole("DELIVERY_AGENT")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"Règle de sécurité stricte : le livreur ne peut pas accéder au code OTP secret du patient.\"}");
                    return;
                }
                try {
                    String otp = deliveryService.getOtpForPatient(order.id(), principal.getUserId(), role);
                    sendJson(exchange, 200, "{\"deliveryCode\":\"" + order.deliveryCode() + "\",\"otpCode\":\"" + otp + "\",\"otpExpiresAt\":\"" + order.otpExpiresAt() + "\",\"attemptsRemaining\":" + order.otpAttemptsRemaining() + "}");
                } catch (Exception e) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Impossible de récupérer l'OTP\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
                return;
            }

            // 6. POST /v1/deliveries/{id}/fail ou cancel
            if (("fail".equalsIgnoreCase(action) || "cancel".equalsIgnoreCase(action)) && "POST".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String reason = JsonHelper.getString(body, "reason");
                if (reason == null || reason.isBlank()) reason = "Incident ou annulation signalée";

                DeliveryOrder failed = deliveryService.failDelivery(order.id(), reason, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                sendJson(exchange, 200, DeliveryJsonMapper.toJson(failed, false));
                return;
            }

            sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Action inconnue\",\"status\":404,\"detail\":\"Action de livraison introuvable: " + action + "\"}");
        }

        private Optional<DeliveryOrder> findDeliveryByTail(String tail) {
            try {
                UUID id = UUID.fromString(tail);
                Optional<DeliveryOrder> byId = deliveryService.findById(id);
                if (byId.isPresent()) return byId;
            } catch (IllegalArgumentException ignored) {}
            return deliveryService.findByCode(tail);
        }

        private boolean isPatientRecipient(MedscanPrincipal principal, DeliveryOrder order) {
            if (!principal.isUserInRole("PATIENT")) return false;
            Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
            return pOpt.isPresent() && pOpt.get().id().equals(order.patientId());
        }

        // ==========================================
        // SYNCHRONISATION HORS-LIGNE & DELTA (MOD-10)
        // ==========================================

        private void handleSyncPull(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            Instant sinceTimestamp = null;
            UUID patientIdFilter = null;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String val = kv[1].trim();
                        if ("since".equalsIgnoreCase(key) && !val.isBlank()) {
                            try {
                                sinceTimestamp = Instant.parse(val);
                            } catch (Exception ignored) {}
                        } else if (("patientId".equalsIgnoreCase(key) || "patient_id".equalsIgnoreCase(key)) && !val.isBlank()) {
                            try {
                                patientIdFilter = UUID.fromString(val);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                SyncPackage pkg = syncService.generateOfflineVault(
                        principal.getUserId(),
                        principal.getName(),
                        role,
                        principal.getTenantId(),
                        patientIdFilter,
                        sinceTimestamp
                );
                sendJson(exchange, 200, SyncJsonMapper.toJson(pkg));
            } catch (SecurityException e) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"type\":\"https://medscan.org/errors/internal\",\"title\":\"Erreur serveur\",\"status\":500,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleSyncPush(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            try {
                SyncBatchRequest request = SyncJsonMapper.parseSyncBatchRequest(body);
                SyncBatchResponse response = syncService.processBatchPush(
                        request,
                        principal.getUserId(),
                        principal.getName(),
                        role,
                        principal.getTenantId()
                );
                sendJson(exchange, 200, SyncJsonMapper.toJson(response));
            } catch (SecurityException e) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"type\":\"https://medscan.org/errors/internal\",\"title\":\"Erreur serveur\",\"status\":500,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleEmergencyCard(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String cleanTail = tail;
            if (cleanTail.contains("?")) {
                cleanTail = cleanTail.substring(0, cleanTail.indexOf("?"));
            }
            if (cleanTail.endsWith("/")) {
                cleanTail = cleanTail.substring(0, cleanTail.length() - 1);
            }

            UUID patientId;
            try {
                patientId = UUID.fromString(cleanTail);
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"Format UUID de patient attendu: " + JsonHelper.escape(cleanTail) + "\"}");
                return;
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                EmergencyCard card = syncService.generateEmergencyCard(
                        patientId,
                        principal.getUserId(),
                        principal.getName(),
                        role,
                        principal.getTenantId()
                );
                sendJson(exchange, 200, SyncJsonMapper.toJson(card));
            } catch (SecurityException e) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès interdit\",\"status\":403,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Patient introuvable\",\"status\":404,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"type\":\"https://medscan.org/errors/internal\",\"title\":\"Erreur serveur\",\"status\":500,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        // ==========================================
        // HUB D'INTEROPÉRABILITÉ HL7 FHIR R4 (MOD-07)
        // ==========================================

        private void handleFhirMetadata(HttpExchange exchange) throws IOException {
            String baseUrl = getFhirBaseUrl(exchange);
            String cap = fhirService.getCapabilityStatement(baseUrl);
            sendFhirJson(exchange, 200, cap);
        }

        private void handleFhirPatientGet(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String cleanTail = tail;
            if (cleanTail.contains("?")) cleanTail = cleanTail.substring(0, cleanTail.indexOf("?"));
            if (cleanTail.endsWith("/")) cleanTail = cleanTail.substring(0, cleanTail.length() - 1);

            UUID patientId;
            try {
                patientId = UUID.fromString(cleanTail);
            } catch (IllegalArgumentException e) {
                sendFhirJson(exchange, 400, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"invalid\",\"diagnostics\":\"Format UUID invalide: " + JsonHelper.escape(cleanTail) + "\"}]}");
                return;
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                String patientFhir = fhirService.getPatient(patientId, principal.getUserId(), principal.getName(), role, principal.getTenantId(), getFhirBaseUrl(exchange));
                sendFhirJson(exchange, 200, patientFhir);
            } catch (SecurityException e) {
                sendFhirJson(exchange, 403, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"forbidden\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (IllegalArgumentException e) {
                sendFhirJson(exchange, 404, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"not-found\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (Exception e) {
                sendFhirJson(exchange, 500, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"exception\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            }
        }

        private void handleFhirPatientSearch(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            String identifier = null;
            String name = null;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String val = kv[1].trim();
                        if ("identifier".equalsIgnoreCase(key)) identifier = val;
                        else if ("name".equalsIgnoreCase(key)) name = val;
                    }
                }
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                String bundle = fhirService.searchPatients(identifier, name, principal.getUserId(), principal.getName(), role, principal.getTenantId(), exchange.getRequestURI().toString());
                sendFhirJson(exchange, 200, bundle);
            } catch (SecurityException e) {
                sendFhirJson(exchange, 403, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"forbidden\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (Exception e) {
                sendFhirJson(exchange, 500, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"exception\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            }
        }

        private void handleFhirPatientCreate(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            try {
                String created = fhirService.createPatient(body, principal.getUserId(), principal.getName(), role, principal.getTenantId(), getFhirBaseUrl(exchange));
                sendFhirJson(exchange, 201, created);
            } catch (SecurityException e) {
                sendFhirJson(exchange, 403, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"forbidden\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (IllegalArgumentException e) {
                sendFhirJson(exchange, 400, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"invalid\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (Exception e) {
                sendFhirJson(exchange, 500, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"exception\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            }
        }

        private void handleFhirObservationSearch(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            String patientParam = null;
            String codeParam = null;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String val = kv[1].trim();
                        if ("patient".equalsIgnoreCase(key) || "subject".equalsIgnoreCase(key)) patientParam = val;
                        else if ("code".equalsIgnoreCase(key)) codeParam = val;
                    }
                }
            }

            UUID patientId = null;
            if (patientParam != null && !patientParam.isBlank()) {
                if (patientParam.startsWith("Patient/")) patientParam = patientParam.substring("Patient/".length());
                try {
                    patientId = UUID.fromString(patientParam);
                } catch (IllegalArgumentException ignored) {}
            }

            if (patientId == null && principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) patientId = pOpt.get().id();
            }

            if (patientId == null) {
                List<Patient> list = clinicalService.searchPatients(principal.getTenantId(), null, principal.getRoles());
                if (!list.isEmpty()) patientId = list.get(0).id();
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                String bundle = fhirService.searchObservations(patientId, codeParam, principal.getUserId(), principal.getName(), role, principal.getTenantId(), exchange.getRequestURI().toString());
                sendFhirJson(exchange, 200, bundle);
            } catch (SecurityException e) {
                sendFhirJson(exchange, 403, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"forbidden\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (IllegalArgumentException e) {
                sendFhirJson(exchange, 404, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"not-found\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (Exception e) {
                sendFhirJson(exchange, 500, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"exception\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            }
        }

        private void handleFhirMedicationRequestSearch(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            String patientParam = null;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && ("patient".equalsIgnoreCase(kv[0].trim()) || "subject".equalsIgnoreCase(kv[0].trim()))) {
                        patientParam = kv[1].trim();
                    }
                }
            }

            UUID patientId = null;
            if (patientParam != null && !patientParam.isBlank()) {
                if (patientParam.startsWith("Patient/")) patientParam = patientParam.substring("Patient/".length());
                try {
                    patientId = UUID.fromString(patientParam);
                } catch (IllegalArgumentException ignored) {}
            }

            if (patientId == null && principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) patientId = pOpt.get().id();
            }

            if (patientId == null) {
                List<Patient> list = clinicalService.searchPatients(principal.getTenantId(), null, principal.getRoles());
                if (!list.isEmpty()) patientId = list.get(0).id();
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                String bundle = fhirService.searchMedicationRequests(patientId, principal.getUserId(), principal.getName(), role, principal.getTenantId(), exchange.getRequestURI().toString());
                sendFhirJson(exchange, 200, bundle);
            } catch (SecurityException e) {
                sendFhirJson(exchange, 403, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"forbidden\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (Exception e) {
                sendFhirJson(exchange, 500, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"exception\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            }
        }

        private void handleFhirMedicationRequestGet(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String cleanTail = tail;
            if (cleanTail.contains("?")) cleanTail = cleanTail.substring(0, cleanTail.indexOf("?"));
            if (cleanTail.endsWith("/")) cleanTail = cleanTail.substring(0, cleanTail.length() - 1);

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                String rxJson = fhirService.getMedicationRequest(cleanTail, principal.getUserId(), principal.getName(), role, principal.getTenantId(), getFhirBaseUrl(exchange));
                sendFhirJson(exchange, 200, rxJson);
            } catch (SecurityException e) {
                sendFhirJson(exchange, 403, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"forbidden\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (IllegalArgumentException e) {
                sendFhirJson(exchange, 404, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"not-found\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (Exception e) {
                sendFhirJson(exchange, 500, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"exception\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            }
        }

        private void handleFhirBundleGet(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            String patientParam = null;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && ("patient".equalsIgnoreCase(kv[0].trim()) || "patientId".equalsIgnoreCase(kv[0].trim()))) {
                        patientParam = kv[1].trim();
                    }
                }
            }

            UUID patientId = null;
            if (patientParam != null && !patientParam.isBlank()) {
                if (patientParam.startsWith("Patient/")) patientParam = patientParam.substring("Patient/".length());
                try {
                    patientId = UUID.fromString(patientParam);
                } catch (IllegalArgumentException ignored) {}
            }

            if (patientId == null && principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) patientId = pOpt.get().id();
            }

            if (patientId == null) {
                List<Patient> list = clinicalService.searchPatients(principal.getTenantId(), null, principal.getRoles());
                if (!list.isEmpty()) patientId = list.get(0).id();
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                String bundle = fhirService.exportPatientRecordAsBundle(patientId, principal.getUserId(), principal.getName(), role, principal.getTenantId(), exchange.getRequestURI().toString());
                sendFhirJson(exchange, 200, bundle);
            } catch (SecurityException e) {
                sendFhirJson(exchange, 403, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"forbidden\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (IllegalArgumentException e) {
                sendFhirJson(exchange, 404, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"not-found\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            } catch (Exception e) {
                sendFhirJson(exchange, 500, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"exception\",\"diagnostics\":\"" + JsonHelper.escape(e.getMessage()) + "\"}]}");
            }
        }

        // ==========================================
        // RENDEZ-VOUS & TÉLÉCONSULTATION (MOD-12)
        // ==========================================

        private void handleListAppointments(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            String patientParam = null;
            String practitionerParam = null;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        if ("patientId".equalsIgnoreCase(kv[0].trim()) || "patient".equalsIgnoreCase(kv[0].trim())) {
                            patientParam = kv[1].trim();
                        } else if ("practitionerId".equalsIgnoreCase(kv[0].trim()) || "practitioner".equalsIgnoreCase(kv[0].trim())) {
                            practitionerParam = kv[1].trim();
                        }
                    }
                }
            }

            List<Appointment> list;
            if (patientParam != null) {
                try {
                    list = appointmentService.listAppointmentsForPatient(UUID.fromString(patientParam));
                } catch (IllegalArgumentException e) {
                    list = Collections.emptyList();
                }
            } else if (practitionerParam != null) {
                try {
                    list = appointmentService.listAppointmentsForPractitioner(UUID.fromString(practitionerParam));
                } catch (IllegalArgumentException e) {
                    list = Collections.emptyList();
                }
            } else if (principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                list = pOpt.isPresent() ? appointmentService.listAppointmentsForPatient(pOpt.get().id()) : Collections.emptyList();
            } else {
                list = appointmentService.listAllAppointments(principal.getTenantId());
            }

            sendJson(exchange, 200, AppointmentJsonMapper.toJsonList(list));
        }

        private void handleCreateAppointment(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String patientIdStr = JsonHelper.getString(body, "patientId");
            String practitionerIdStr = JsonHelper.getString(body, "practitionerId");
            String practitionerName = JsonHelper.getString(body, "practitionerName");
            String practitionerRole = JsonHelper.getString(body, "practitionerRole");
            String appointmentType = JsonHelper.getString(body, "appointmentType");
            String scheduledAtStr = JsonHelper.getString(body, "scheduledAt");
            String durationMinutesStr = JsonHelper.getString(body, "durationMinutes");
            String reason = JsonHelper.getString(body, "reason");

            UUID patientId = null;
            if (patientIdStr != null && !patientIdStr.isBlank()) {
                patientId = UUID.fromString(patientIdStr);
            } else if (principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) patientId = pOpt.get().id();
            }

            if (patientId == null) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"L'identifiant du patient est requis.\"}");
                return;
            }

            UUID practitionerId = (practitionerIdStr != null && !practitionerIdStr.isBlank()) ? UUID.fromString(practitionerIdStr) : principal.getUserId();
            Instant scheduledAt = (scheduledAtStr != null && !scheduledAtStr.isBlank()) ? Instant.parse(scheduledAtStr) : Instant.now().plusSeconds(86400);
            int durationMinutes = 30;
            if (durationMinutesStr != null && !durationMinutesStr.isBlank()) {
                try { durationMinutes = Integer.parseInt(durationMinutesStr); } catch (NumberFormatException ignored) {}
            }

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();

            try {
                Appointment appt = appointmentService.bookAppointment(
                        patientId, practitionerId, practitionerName, practitionerRole,
                        appointmentType, scheduledAt, durationMinutes, reason,
                        principal.getUserId(), principal.getName(), role, principal.getTenantId()
                );
                sendJson(exchange, 201, AppointmentJsonMapper.toJson(appt));
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleAppointmentAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();
            String role = principal.getRoles().isEmpty() ? "USER" : principal.getRoles().iterator().next();

            String cleanTail = tail.startsWith("/") ? tail.substring(1) : tail;

            try {
                if (cleanTail.contains("/confirm") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    UUID id = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/confirm")));
                    Appointment appt = appointmentService.confirmAppointment(id, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, AppointmentJsonMapper.toJson(appt));
                } else if (cleanTail.contains("/start-teleconsult") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    UUID id = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/start-teleconsult")));
                    Appointment appt = appointmentService.startTeleconsultation(id, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, AppointmentJsonMapper.toJson(appt));
                } else if (cleanTail.contains("/cancel") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    UUID id = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/cancel")));
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String reason = JsonHelper.getString(body, "reason");
                    Appointment appt = appointmentService.cancelAppointment(id, reason != null ? reason : "Annulé par l'utilisateur", principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, AppointmentJsonMapper.toJson(appt));
                } else if (cleanTail.contains("/complete") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    UUID id = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/complete")));
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String notes = JsonHelper.getString(body, "notes");
                    Appointment appt = appointmentService.completeAppointment(id, notes, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, AppointmentJsonMapper.toJson(appt));
                } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    UUID id = UUID.fromString(cleanTail);
                    Optional<Appointment> opt = appointmentService.findAppointmentById(id);
                    if (opt.isPresent()) {
                        sendJson(exchange, 200, AppointmentJsonMapper.toJson(opt.get()));
                    } else {
                        sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Rendez-vous introuvable.\"}");
                    }
                } else {
                    sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode non supportée.\"}");
                }
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        // ==========================================
        // LABORATOIRE D'ANALYSES MÉDICALES (MOD-13)
        // ==========================================

        private void handleListLabOrders(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            String patientParam = null;
            boolean pendingOnly = false;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        if ("patientId".equalsIgnoreCase(kv[0].trim()) || "patient".equalsIgnoreCase(kv[0].trim())) {
                            patientParam = kv[1].trim();
                        } else if ("pending".equalsIgnoreCase(kv[0].trim())) {
                            pendingOnly = "true".equalsIgnoreCase(kv[1].trim());
                        }
                    }
                }
            }

            List<LabOrder> orders;
            if (patientParam != null) {
                try {
                    orders = labService.listOrdersForPatient(UUID.fromString(patientParam));
                } catch (IllegalArgumentException e) {
                    orders = Collections.emptyList();
                }
            } else if (pendingOnly) {
                orders = labService.listPendingOrders(principal.getTenantId());
            } else {
                orders = labService.listAllOrders(principal.getTenantId());
            }

            sendJson(exchange, 200, LabJsonMapper.ordersListToJson(orders));
        }

        private void handleCreateLabOrder(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            if (!principal.isUserInRole("DOCTOR") && !principal.isUserInRole("ADMIN") && !principal.isUserInRole("SUPER_ADMIN")) {
                sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Interdit\",\"status\":403,\"detail\":\"Seul un médecin peut prescrire une analyse biologique.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String patientIdStr = JsonHelper.getString(body, "patientId");
            String testCategory = JsonHelper.getString(body, "testCategory");
            String testName = JsonHelper.getString(body, "testName");
            if (testName == null && body.contains("\"items\"")) {
                testName = "Bilan Biologique Multi-Analyses";
            }
            if (testCategory == null) {
                testCategory = "BIOLOGIE_GENERALE";
            }
            String priority = JsonHelper.getString(body, "priority");
            String clinicalIndication = JsonHelper.getString(body, "clinicalIndication");

            if (patientIdStr == null || testName == null) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"patientId et testName sont requis.\"}");
                return;
            }

            try {
                LabOrder order = labService.createLabOrder(
                        UUID.fromString(patientIdStr), testCategory, testName, priority,
                        clinicalIndication, principal.getUserId(), principal.getName(), principal.getTenantId()
                );
                sendJson(exchange, 201, LabJsonMapper.orderToJson(order));
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleLabOrderAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();
            String role = principal.getRoles().isEmpty() ? "USER" : principal.getRoles().iterator().next();

            String cleanTail = tail.startsWith("/") ? tail.substring(1) : tail;

            try {
                if (cleanTail.contains("/collect-sample") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    UUID id = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/collect-sample")));
                    LabOrder order = labService.collectSample(id, principal.getUserId(), principal.getName(), role, principal.getTenantId());
                    sendJson(exchange, 200, LabJsonMapper.orderToJson(order));
                } else if (cleanTail.contains("/record-results") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    UUID id = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/record-results")));
                    Optional<LabOrder> opt = labService.findById(id);
                    if (opt.isEmpty()) {
                        sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Analyse introuvable.\"}");
                        return;
                    }
                    sendJson(exchange, 200, LabJsonMapper.orderToJson(opt.get()));
                } else if ((cleanTail.contains("/validate-results") || cleanTail.contains("/validate")) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    if (!principal.isUserInRole("LAB_TECHNICIAN") && !principal.isUserInRole("DOCTOR") && !principal.isUserInRole("ADMIN") && !principal.isUserInRole("SUPER_ADMIN")) {
                        sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Interdit\",\"status\":403,\"detail\":\"Seul un technicien de laboratoire ou médecin peut valider les résultats.\"}");
                        return;
                    }
                    int idx = cleanTail.contains("/validate-results") ? cleanTail.indexOf("/validate-results") : cleanTail.indexOf("/validate");
                    UUID id = UUID.fromString(cleanTail.substring(0, idx));
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                    List<LabResultItem> results = new ArrayList<>();
                    String paramCode = JsonHelper.getString(body, "paramCode");
                    String paramName = JsonHelper.getString(body, "paramName");
                    String textValue = JsonHelper.getString(body, "textValue");
                    String numericValueStr = JsonHelper.getString(body, "numericValue");
                    String unit = JsonHelper.getString(body, "unit");
                    String loinc = JsonHelper.getString(body, "loincCode");

                    if (paramCode != null) {
                        Double numVal = (numericValueStr != null) ? Double.parseDouble(numericValueStr) : null;
                        if (numVal != null) {
                            results.add(LabResultItem.ofNumeric(paramCode, paramName != null ? paramName : paramCode, loinc, numVal, unit, null, null, null, null, "Résultat saisi"));
                        } else {
                            results.add(LabResultItem.ofText(paramCode, paramName != null ? paramName : paramCode, loinc, textValue, false, false, "Résultat saisi"));
                        }
                    } else if (body.contains("\"critical\"") || body.contains("CRITICAL") || body.contains("Positif") || body.contains("POSITIF")) {
                        results.add(LabResultItem.ofText("CRITICAL_ALERT", "Anomalie Biologique Critique", "critical", "Valeur critique confirmée par le laboratoire", true, true, "Prise en charge urgente requise"));
                    } else {
                        results.add(LabResultItem.ofText("ANALYSIS_COMPLETE", "Compte-rendu d'analyse", "unknown", "Analyses conformes", false, false, "Validation technique"));
                    }

                    LabOrder validated = labService.validateResults(id, principal.getUserId(), principal.getName(), results, principal.getTenantId());

                    if (validated.hasCriticalAlert()) {
                        messagingNotificationService.dispatchNotification(
                                validated.prescriberId(),
                                "DOCTOR",
                                "CRITICAL_LAB_ALERT",
                                "CRITICAL",
                                "Résultat d'analyse critique pour " + validated.patientName(),
                                "L'analyse " + validated.orderCode() + " présente des valeurs critiques nécessitant une prise en charge rapide.",
                                "LabOrder",
                                validated.orderCode(),
                                principal.getTenantId()
                        );
                    }

                    sendJson(exchange, 200, LabJsonMapper.orderToJson(validated));
                } else if (cleanTail.contains("/fhir") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String idOrCode = cleanTail.substring(0, cleanTail.indexOf("/fhir"));
                    Optional<LabOrder> opt = labService.findByCode(idOrCode);
                    if (opt.isEmpty()) {
                        try { opt = labService.findById(UUID.fromString(idOrCode)); } catch (IllegalArgumentException ignored) {}
                    }
                    if (opt.isPresent()) {
                        String fhirBundle = labService.toFhirLaboratoryBundle(opt.get(), getFhirBaseUrl(exchange));
                        sendFhirJson(exchange, 200, fhirBundle);
                    } else {
                        sendFhirJson(exchange, 404, "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"not-found\",\"diagnostics\":\"Analyse introuvable.\"}]}");
                    }
                } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Optional<LabOrder> opt = labService.findByCode(cleanTail);
                    if (opt.isEmpty()) {
                        try { opt = labService.findById(UUID.fromString(cleanTail)); } catch (IllegalArgumentException ignored) {}
                    }
                    if (opt.isPresent()) {
                        sendJson(exchange, 200, LabJsonMapper.orderToJson(opt.get()));
                    } else {
                        sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Analyse introuvable.\"}");
                    }
                } else {
                    sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode non supportée.\"}");
                }
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        // ==========================================
        // SURVEILLANCE ÉPIDÉMIOLOGIQUE & DHIS2 (MOD-14)
        // ==========================================

        private void handleListEpidemiologyCases(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            String diseaseCode = null;
            String region = null;

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        if ("diseaseCode".equalsIgnoreCase(kv[0].trim()) || "disease".equalsIgnoreCase(kv[0].trim())) {
                            diseaseCode = kv[1].trim();
                        } else if ("region".equalsIgnoreCase(kv[0].trim())) {
                            region = kv[1].trim();
                        }
                    }
                }
            }

            List<EpidemiologicalCase> cases = epidemiologyService.listCases(principal.getTenantId(), diseaseCode, region);
            sendJson(exchange, 200, EpidemiologyJsonMapper.casesListToJson(cases));
        }

        private void handleReportEpidemiologyCase(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String region = JsonHelper.getString(body, "region");
            String district = JsonHelper.getString(body, "district");
            String diseaseCode = JsonHelper.getString(body, "diseaseCode");
            String diseaseName = JsonHelper.getString(body, "diseaseName");
            String detectionSource = JsonHelper.getString(body, "detectionSource");
            String patientAgeStr = JsonHelper.getString(body, "patientAge");
            String patientGender = JsonHelper.getString(body, "patientGender");
            String outcome = JsonHelper.getString(body, "outcome");

            int age = 30;
            if (patientAgeStr != null) {
                try { age = Integer.parseInt(patientAgeStr); } catch (NumberFormatException ignored) {}
            }

            String role = principal.getRoles().isEmpty() ? "DOCTOR" : principal.getRoles().iterator().next();

            try {
                EpidemiologicalCase c = epidemiologyService.reportCase(
                        principal.getTenantId(), region, district, diseaseCode, diseaseName,
                        detectionSource, age, patientGender, outcome,
                        principal.getUserId(), principal.getName(), role
                );
                sendJson(exchange, 201, EpidemiologyJsonMapper.caseToJson(c));
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleEpidemiologySummary(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            Map<String, Object> summary = epidemiologyService.getEpidemiologicalSummary(principal.getTenantId());
            sendJson(exchange, 200, EpidemiologyJsonMapper.summaryToJson(summary));
        }

        private void handleEpidemiologyDhis2Export(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;

            String rawQuery = exchange.getRequestURI().getQuery();
            String period = "2026W42";
            String orgUnit = "ou-bfa-centre-01";

            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        if ("period".equalsIgnoreCase(kv[0].trim())) period = kv[1].trim();
                        else if ("orgUnit".equalsIgnoreCase(kv[0].trim())) orgUnit = kv[1].trim();
                    }
                }
            }

            String dhis2Json = epidemiologyService.exportDhis2DataValueSet(period, orgUnit);
            sendJson(exchange, 200, dhis2Json);
        }

        // ==========================================
        // MESSAGERIE SÉCURISÉE (MOD-15)
        // ==========================================

        private void handleListConversations(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            var list = messagingNotificationService.listConversationsForUser(principal.getUserId());
            sendJson(exchange, 200, MessagingJsonMapper.conversationSummariesListToJson(list));
        }

        private void handleListContacts(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String role = principal.getRoles().isEmpty() ? "PATIENT" : principal.getRoles().iterator().next();
            var contacts = messagingNotificationService.listEligibleContacts(principal.getUserId(), role);
            sendJson(exchange, 200, MessagingJsonMapper.contactsListToJson(contacts));
        }

        private void handleListMessages(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            List<ClinicalMessage> messages = messagingNotificationService.listUserMessages(principal.getUserId());
            sendJson(exchange, 200, MessagingJsonMapper.messagesListToJson(messages));
        }

        private void handleSendMessage(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String convIdStr = JsonHelper.getString(body, "conversationId");
            String recipientIdStr = JsonHelper.getString(body, "recipientId");
            if (recipientIdStr == null) recipientIdStr = JsonHelper.getString(body, "receiverId");
            String recipientName = JsonHelper.getString(body, "recipientName");
            if (recipientName == null) recipientName = "Destinataire";
            String recipientRole = JsonHelper.getString(body, "recipientRole");
            if (recipientRole == null) recipientRole = "PATIENT";
            String patientIdStr = JsonHelper.getString(body, "patientId");
            String subject = JsonHelper.getString(body, "subject");
            String content = JsonHelper.getString(body, "content");
            if (content == null) content = JsonHelper.getString(body, "body");
            String priority = JsonHelper.getString(body, "priority");

            if (recipientIdStr == null || content == null) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"recipientId (ou receiverId) et content (ou body) sont requis.\"}");
                return;
            }

            UUID convId = (convIdStr != null && !convIdStr.isBlank()) ? UUID.fromString(convIdStr) : null;
            UUID recipientId = UUID.fromString(recipientIdStr);
            UUID patientId = (patientIdStr != null && !patientIdStr.isBlank()) ? UUID.fromString(patientIdStr) : null;
            String senderRole = principal.getRoles().isEmpty() ? "USER" : principal.getRoles().iterator().next();

            try {
                ClinicalMessage message = messagingNotificationService.sendMessage(
                        convId, principal.getUserId(), principal.getName(), senderRole,
                        recipientId, recipientName, recipientRole, patientId,
                        subject, content, priority, principal.getTenantId()
                );
                sendJson(exchange, 201, MessagingJsonMapper.messageToJson(message));
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleConversationMessages(HttpExchange exchange, String conversationOrPeerIdStr) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            try {
                UUID convOrPeerId = UUID.fromString(conversationOrPeerIdStr.trim());
                List<ClinicalMessage> messages = messagingNotificationService.listMessagesInConversation(convOrPeerId);
                if (messages.isEmpty()) {
                    messages = messagingNotificationService.listMessagesWithPeer(principal.getUserId(), convOrPeerId);
                }
                sendJson(exchange, 200, MessagingJsonMapper.messagesListToJson(messages));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"UUID invalide.\"}");
            }
        }

        // ==========================================
        // NOTIFICATIONS & ALERTES MULTI-ENTITÉS (MOD-15)
        // ==========================================

        private void handleListNotifications(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String rawQuery = exchange.getRequestURI().getQuery();
            boolean unreadOnly = false;
            if (rawQuery != null && !rawQuery.isBlank()) {
                for (String param : rawQuery.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "unreadOnly".equalsIgnoreCase(kv[0].trim())) {
                        unreadOnly = "true".equalsIgnoreCase(kv[1].trim());
                    }
                }
            }

            List<InAppNotification> notifs = messagingNotificationService.listNotificationsForUser(principal.getUserId(), unreadOnly);
            sendJson(exchange, 200, MessagingJsonMapper.notificationsListToJson(notifs));
        }

        private void handleNotificationUnreadCount(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            int count = messagingNotificationService.getUnreadNotificationCount(principal.getUserId());
            sendJson(exchange, 200, "{\"unreadCount\":" + count + "}");
        }

        private void handleNotificationMarkAllRead(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            int marked = messagingNotificationService.markAllNotificationsAsRead(principal.getUserId());
            sendJson(exchange, 200, "{\"markedCount\":" + marked + ",\"markedRead\":" + marked + "}");
        }

        private void handleNotificationAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String cleanTail = tail.startsWith("/") ? tail.substring(1) : tail;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String idStr = cleanTail.contains("/read") ? cleanTail.substring(0, cleanTail.indexOf("/read")) : cleanTail;
                    UUID id = UUID.fromString(idStr);
                    Optional<InAppNotification> opt = messagingNotificationService.markNotificationAsRead(id, principal.getUserId());
                    if (opt.isPresent()) {
                        sendJson(exchange, 200, MessagingJsonMapper.notificationToJson(opt.get()));
                    } else {
                        sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Notification introuvable.\"}");
                    }
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Identifiant invalide\",\"status\":400,\"detail\":\"UUID invalide.\"}");
                }
            } else {
                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode non supportée.\"}");
            }
        }

        // ==========================================
        // HANDLERS MOD-16 : ORIENTATIONS & TRANSFERTS MÉDICAUX
        // ==========================================

        private void handleListReferrals(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String query = exchange.getRequestURI().getQuery();
            UUID patientParam = null;
            UUID doctorParam = null;
            UUID tenantParam = null;
            Boolean incomingOnly = null;

            if (query != null && !query.isBlank()) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        try {
                            if ("patientId".equalsIgnoreCase(kv[0])) patientParam = UUID.fromString(kv[1]);
                            else if ("doctorId".equalsIgnoreCase(kv[0])) doctorParam = UUID.fromString(kv[1]);
                            else if ("tenantId".equalsIgnoreCase(kv[0])) tenantParam = UUID.fromString(kv[1]);
                            else if ("incomingOnly".equalsIgnoreCase(kv[0])) incomingOnly = Boolean.parseBoolean(kv[1]);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }

            if (patientParam == null && principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) patientParam = pOpt.get().id();
            }

            if (tenantParam == null && !principal.isUserInRole("SUPER_ADMIN") && !principal.isUserInRole("PATIENT")) {
                tenantParam = principal.getTenantId();
            }

            List<MedicalReferral> list = referralService.listReferrals(tenantParam, patientParam, doctorParam, incomingOnly);
            sendJson(exchange, 200, ReferralJsonMapper.listToJson(list));
        }

        private void handleCreateReferral(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String patientIdStr = JsonHelper.getString(body, "patientId");
            String targetTenantIdStr = JsonHelper.getString(body, "targetTenantId");
            String sourceDepartment = JsonHelper.getString(body, "sourceDepartment");
            String targetDepartment = JsonHelper.getString(body, "targetDepartment");
            String targetDoctorIdStr = JsonHelper.getString(body, "targetDoctorId");
            String targetDoctorName = JsonHelper.getString(body, "targetDoctorName");
            String reason = JsonHelper.getString(body, "reason");
            String priority = JsonHelper.getString(body, "priority");
            String clinicalSummary = JsonHelper.getString(body, "clinicalSummary");
            String doctorLicense = JsonHelper.getString(body, "doctorLicenseNumber");

            if (patientIdStr == null || patientIdStr.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"L'identifiant du patient (patientId) est requis.\"}");
                return;
            }
            if (targetDepartment == null || targetDepartment.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"Le service ou la spécialité cible (targetDepartment) est requis.\"}");
                return;
            }
            if (reason == null || reason.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"Le motif de l'orientation (reason) est requis.\"}");
                return;
            }

            UUID patientId = UUID.fromString(patientIdStr.trim());
            UUID targetTenantId = (targetTenantIdStr != null && !targetTenantIdStr.isBlank()) ? UUID.fromString(targetTenantIdStr.trim()) : null;
            UUID targetDoctorId = (targetDoctorIdStr != null && !targetDoctorIdStr.isBlank()) ? UUID.fromString(targetDoctorIdStr.trim()) : null;
            UUID sourceTenantId = principal.getTenantId();
            if (sourceTenantId == null) {
                Patient p = clinicalService.findPatientById(patientId).orElse(null);
                sourceTenantId = (p != null) ? p.tenantId() : null;
            }

            try {
                MedicalReferral ref = referralService.createReferral(
                        patientId,
                        principal.getUserId(),
                        principal.getName(),
                        doctorLicense != null ? doctorLicense : "OM-BF-REF",
                        sourceTenantId,
                        sourceDepartment != null ? sourceDepartment : "Médecine Générale",
                        targetTenantId,
                        targetDepartment,
                        targetDoctorId,
                        targetDoctorName,
                        reason,
                        priority != null ? priority : "ROUTINE",
                        clinicalSummary
                );
                sendJson(exchange, 201, ReferralJsonMapper.referralToJson(ref));
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleReferralAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String cleanTail = tail.startsWith("/") ? tail.substring(1) : tail;
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                Optional<MedicalReferral> opt = Optional.empty();
                try {
                    opt = referralService.findReferralById(UUID.fromString(cleanTail));
                } catch (IllegalArgumentException ignored) {
                    opt = referralService.findReferralByCode(cleanTail);
                }
                if (opt.isPresent()) {
                    sendJson(exchange, 200, ReferralJsonMapper.referralToJson(opt.get()));
                } else {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Orientation médicale introuvable.\"}");
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                UUID refId = null;
                String action = "";

                if (cleanTail.contains("/accept")) {
                    refId = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/accept")));
                    action = "ACCEPTED";
                } else if (cleanTail.contains("/reject")) {
                    refId = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/reject")));
                    action = "REJECTED";
                } else if (cleanTail.contains("/complete")) {
                    refId = UUID.fromString(cleanTail.substring(0, cleanTail.indexOf("/complete")));
                    action = "COMPLETED";
                }

                if (refId == null) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Action inconnue\",\"status\":400,\"detail\":\"Action sur l'orientation non reconnue.\"}");
                    return;
                }

                String rejectionReason = JsonHelper.getString(body, "reason");
                String role = principal.getRoles().isEmpty() ? "DOCTOR" : principal.getRoles().iterator().next();

                try {
                    MedicalReferral updated = referralService.updateReferralStatus(
                            refId, action, rejectionReason, principal.getUserId(), principal.getName(), role, principal.getTenantId()
                    );
                    sendJson(exchange, 200, ReferralJsonMapper.referralToJson(updated));
                } catch (Exception e) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
            } else {
                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode non supportée.\"}");
            }
        }

        // ==========================================
        // HANDLERS MOD-17 : CERTIFICATS MÉDICAUX & CACHET D'HÔPITAL
        // ==========================================

        private void handleListCertificates(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String query = exchange.getRequestURI().getQuery();
            UUID patientParam = null;
            UUID doctorParam = null;
            UUID tenantParam = null;

            if (query != null && !query.isBlank()) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        try {
                            if ("patientId".equalsIgnoreCase(kv[0])) patientParam = UUID.fromString(kv[1]);
                            else if ("doctorId".equalsIgnoreCase(kv[0])) doctorParam = UUID.fromString(kv[1]);
                            else if ("tenantId".equalsIgnoreCase(kv[0])) tenantParam = UUID.fromString(kv[1]);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }

            if (patientParam == null && principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) patientParam = pOpt.get().id();
            }

            if (tenantParam == null && !principal.isUserInRole("SUPER_ADMIN") && !principal.isUserInRole("PATIENT")) {
                tenantParam = principal.getTenantId();
            }

            List<MedicalCertificate> list = certificateService.listCertificates(tenantParam, patientParam, doctorParam);
            sendJson(exchange, 200, CertificateJsonMapper.listToJson(list));
        }

        private void handleIssueCertificate(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String patientIdStr = JsonHelper.getString(body, "patientId");
            String tenantIdStr = JsonHelper.getString(body, "tenantId");
            String certType = JsonHelper.getString(body, "certificateType");
            String diagnosisSummary = JsonHelper.getString(body, "diagnosisSummary");
            String startDateStr = JsonHelper.getString(body, "startDate");
            Integer durationDays = JsonHelper.getInt(body, "durationDays");
            String recipientType = JsonHelper.getString(body, "recipientType");
            String recipientName = JsonHelper.getString(body, "recipientName");
            String restrictions = JsonHelper.getString(body, "restrictions");
            String doctorLicense = JsonHelper.getString(body, "doctorLicenseNumber");

            if (patientIdStr == null || patientIdStr.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"L'identifiant du patient (patientId) est requis.\"}");
                return;
            }

            UUID patientId = UUID.fromString(patientIdStr.trim());
            UUID tenantId = (tenantIdStr != null && !tenantIdStr.isBlank()) ? UUID.fromString(tenantIdStr.trim()) : principal.getTenantId();
            if (tenantId == null) {
                Patient p = clinicalService.findPatientById(patientId).orElse(null);
                tenantId = (p != null) ? p.tenantId() : null;
            }

            Instant startDate = (startDateStr != null && !startDateStr.isBlank()) ? Instant.parse(startDateStr) : Instant.now();
            int days = (durationDays != null && durationDays > 0) ? durationDays : 3;

            try {
                MedicalCertificate cert = certificateService.issueCertificate(
                        patientId,
                        principal.getUserId(),
                        principal.getName(),
                        doctorLicense != null ? doctorLicense : "OM-BF-5412",
                        tenantId,
                        certType != null ? certType : "SICK_LEAVE",
                        diagnosisSummary != null ? diagnosisSummary : "État de santé nécessitant repos médical strict",
                        startDate,
                        days,
                        recipientType != null ? recipientType : "EMPLOYER",
                        recipientName != null ? recipientName : "Direction des Ressources Humaines / Administration",
                        restrictions != null ? restrictions : "Repos strict à domicile, cessation de toute activité"
                );
                sendJson(exchange, 201, CertificateJsonMapper.certificateToJson(cert));
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleCertificateAction(HttpExchange exchange, String tail) throws IOException {
            String cleanTail = tail.startsWith("/") ? tail.substring(1) : tail;
            boolean isDocument = cleanTail.contains("/document");
            String idOrCode = isDocument ? cleanTail.substring(0, cleanTail.indexOf("/document")) : cleanTail;

            Optional<MedicalCertificate> opt = Optional.empty();
            try {
                opt = certificateService.findCertificateById(UUID.fromString(idOrCode));
            } catch (IllegalArgumentException ignored) {
                opt = certificateService.findCertificateByCode(idOrCode);
            }

            if (opt.isEmpty()) {
                sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Certificat médical introuvable.\"}");
                return;
            }

            if (isDocument) {
                String html = CertificateJsonMapper.renderOfficialPrintableHtml(opt.get());
                sendHtml(exchange, 200, html);
            } else {
                sendJson(exchange, 200, CertificateJsonMapper.certificateToJson(opt.get()));
            }
        }

        private void handleVerifyCertificate(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String code = null;
            String hash = null;
            if (query != null && !query.isBlank()) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        if ("code".equalsIgnoreCase(kv[0])) code = kv[1];
                        else if ("hash".equalsIgnoreCase(kv[0])) hash = kv[1];
                    }
                }
            }

            if (code == null || code.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Paramètres manquants\",\"status\":400,\"detail\":\"Le code du document est requis.\"}");
                return;
            }

            Optional<MedicalCertificate> opt = certificateService.findCertificateByCode(code);
            if (opt.isEmpty()) {
                sendJson(exchange, 404, "{\"valid\":false,\"detail\":\"Document non répertorié dans le registre hospitalier MedScan.\"}");
                return;
            }

            MedicalCertificate cert = opt.get();
            boolean hashMatch = (hash == null || hash.isBlank()) || (cert.hospitalSeal() != null && hash.equalsIgnoreCase(cert.hospitalSeal().verificationHash()));

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"valid\":").append(hashMatch).append(",");
            sb.append("\"certificateCode\":\"").append(JsonHelper.escape(cert.certificateCode())).append("\",");
            sb.append("\"certificateType\":\"").append(JsonHelper.escape(cert.certificateType())).append("\",");
            sb.append("\"hospitalName\":\"").append(JsonHelper.escape(cert.hospitalName())).append("\",");
            sb.append("\"doctorName\":\"").append(JsonHelper.escape(cert.doctorName())).append("\",");
            sb.append("\"patientName\":\"").append(JsonHelper.escape(cert.patientName())).append("\",");
            sb.append("\"durationDays\":").append(cert.durationDays()).append(",");
            sb.append("\"stampedAt\":\"").append(cert.hospitalSeal() != null ? cert.hospitalSeal().stampedAt() : cert.createdAt()).append("\",");
            sb.append("\"status\":\"").append(JsonHelper.escape(cert.status())).append("\"");
            sb.append("}");
            sendJson(exchange, 200, sb.toString());
        }

        // ==========================================
        // HANDLERS MOD-18 : RÉCLAMATIONS & AVIS PATIENTS
        // ==========================================

        private void handleListGrievances(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String query = exchange.getRequestURI().getQuery();
            UUID patientParam = null;
            UUID doctorParam = null;
            UUID tenantParam = null;
            String statusFilter = null;

            if (query != null && !query.isBlank()) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        try {
                            if ("patientId".equalsIgnoreCase(kv[0])) patientParam = UUID.fromString(kv[1]);
                            else if ("doctorId".equalsIgnoreCase(kv[0])) doctorParam = UUID.fromString(kv[1]);
                            else if ("tenantId".equalsIgnoreCase(kv[0])) tenantParam = UUID.fromString(kv[1]);
                            else if ("status".equalsIgnoreCase(kv[0])) statusFilter = kv[1];
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }

            if (principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                patientParam = pOpt.map(Patient::id).orElse(principal.getUserId());
            } else if (principal.isUserInRole("TENANT_ADMIN") && tenantParam == null) {
                tenantParam = principal.getTenantId();
            }

            boolean isSuperAdmin = principal.isUserInRole("SUPER_ADMIN");
            List<PatientGrievance> list = grievanceService.listGrievances(tenantParam, isSuperAdmin, patientParam, doctorParam, statusFilter);
            sendJson(exchange, 200, GrievanceJsonMapper.listToJson(list));
        }

        private void handleSubmitGrievance(HttpExchange exchange) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String patientIdStr = JsonHelper.getString(body, "patientId");
            String doctorIdStr = JsonHelper.getString(body, "doctorId");
            String doctorName = JsonHelper.getString(body, "doctorName");
            String tenantIdStr = JsonHelper.getString(body, "tenantId");
            String consultationIdStr = JsonHelper.getString(body, "consultationId");
            String grievanceType = JsonHelper.getString(body, "grievanceType");
            String category = JsonHelper.getString(body, "category");
            Integer rating = JsonHelper.getInt(body, "rating");
            String title = JsonHelper.getString(body, "title");
            String description = JsonHelper.getString(body, "description");

            UUID patientId = null;
            if (patientIdStr != null && !patientIdStr.isBlank()) {
                try { patientId = UUID.fromString(patientIdStr.trim()); } catch (IllegalArgumentException ignored) {}
            } else if (principal.isUserInRole("PATIENT")) {
                Optional<Patient> pOpt = clinicalService.findPatientByLinkedUserId(principal.getUserId());
                if (pOpt.isPresent()) patientId = pOpt.get().id();
            }

            if (patientId == null) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"L'identifiant du patient est requis.\"}");
                return;
            }
            if (doctorIdStr == null || doctorIdStr.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"L'identifiant du médecin (doctorId) est requis.\"}");
                return;
            }
            if (title == null || title.isBlank() || description == null || description.isBlank()) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Requête invalide\",\"status\":400,\"detail\":\"Le titre et la description détaillée sont requis.\"}");
                return;
            }

            UUID doctorId = UUID.fromString(doctorIdStr.trim());
            UUID tenantId = (tenantIdStr != null && !tenantIdStr.isBlank()) ? UUID.fromString(tenantIdStr.trim()) : null;
            UUID consultationId = (consultationIdStr != null && !consultationIdStr.isBlank()) ? UUID.fromString(consultationIdStr.trim()) : null;

            try {
                PatientGrievance g = grievanceService.submitGrievance(
                        patientId,
                        doctorId,
                        doctorName,
                        tenantId,
                        consultationId,
                        grievanceType != null ? grievanceType : "COMPLAINT",
                        category != null ? category : "CARE_QUALITY",
                        rating != null ? rating : 3,
                        title,
                        description
                );
                sendJson(exchange, 201, GrievanceJsonMapper.grievanceToJson(g));
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleGrievanceAction(HttpExchange exchange, String tail) throws IOException {
            MedscanSecurityContext context = authenticate(exchange);
            if (context == null) return;
            MedscanPrincipal principal = (MedscanPrincipal) context.getUserPrincipal();

            String cleanTail = tail.startsWith("/") ? tail.substring(1) : tail;
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                Optional<PatientGrievance> opt = Optional.empty();
                try {
                    opt = grievanceService.findGrievanceById(UUID.fromString(cleanTail));
                } catch (IllegalArgumentException ignored) {
                    opt = grievanceService.findGrievanceByCode(cleanTail);
                }
                if (opt.isPresent()) {
                    sendJson(exchange, 200, GrievanceJsonMapper.grievanceToJson(opt.get()));
                } else {
                    sendJson(exchange, 404, "{\"type\":\"https://medscan.org/errors/not-found\",\"title\":\"Non trouvé\",\"status\":404,\"detail\":\"Réclamation introuvable.\"}");
                }
            } else if ("PUT".equalsIgnoreCase(method) && cleanTail.contains("/review")) {
                if (!principal.isUserInRole("TENANT_ADMIN") && !principal.isUserInRole("SUPER_ADMIN")) {
                    sendJson(exchange, 403, "{\"type\":\"https://medscan.org/errors/forbidden\",\"title\":\"Accès refusé\",\"status\":403,\"detail\":\"Seul l'administrateur de l'hôpital ou le Super Admin peut traiter les réclamations.\"}");
                    return;
                }

                String idStr = cleanTail.substring(0, cleanTail.indexOf("/review"));
                UUID grievanceId = UUID.fromString(idStr);
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                String status = JsonHelper.getString(body, "status");
                String adminNotes = JsonHelper.getString(body, "adminNotes");
                String actionTaken = JsonHelper.getString(body, "actionTaken");

                try {
                    PatientGrievance reviewed = grievanceService.reviewGrievance(
                            grievanceId,
                            principal.getUserId(),
                            principal.getName(),
                            status != null ? status : "RESOLVED",
                            adminNotes,
                            actionTaken
                    );
                    sendJson(exchange, 200, GrievanceJsonMapper.grievanceToJson(reviewed));
                } catch (Exception e) {
                    sendJson(exchange, 400, "{\"type\":\"https://medscan.org/errors/bad-request\",\"title\":\"Erreur\",\"status\":400,\"detail\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
                }
            } else {
                sendJson(exchange, 405, "{\"type\":\"https://medscan.org/errors/method-not-allowed\",\"title\":\"Méthode non autorisée\",\"status\":405,\"detail\":\"Méthode non supportée.\"}");
            }
        }

        private void sendHtml(HttpExchange exchange, int statusCode, String htmlContent) throws IOException {
            byte[] bytes = htmlContent.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getFhirBaseUrl(HttpExchange exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (host == null || host.isBlank()) {
                host = "localhost:" + port;
            }
            String proto = "http";
            if (host.contains("onrender.com") || "https".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"))) {
                proto = "https";
            }
            return proto + "://" + host + CONTEXT_PATH + "/fhir/r4";
        }

        private void sendFhirJson(HttpExchange exchange, int statusCode, String fhirJson) throws IOException {
            byte[] bytes = fhirJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", FhirConstants.MIME_FHIR_JSON);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
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
