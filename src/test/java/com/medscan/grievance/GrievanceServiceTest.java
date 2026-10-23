package com.medscan.grievance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;
import com.medscan.messaging.InAppNotification;
import com.medscan.messaging.MessagingNotificationService;
import com.medscan.security.service.SeedUserRegistry;
import com.medscan.security.tenant.TenantRegistry;

class GrievanceServiceTest {

    private ClinicalService clinicalService;
    private TenantRegistry tenantRegistry;
    private MessagingNotificationService messagingService;
    private SeedUserRegistry userRegistry;
    private GrievanceService grievanceService;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"); // Fatou Ouedraogo
    private static final UUID PATIENT_USER_ID = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4");
    private static final UUID DOCTOR_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");  // Dr Seydou Traore
    private static final UUID HOSPITAL_OUAGA = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID ADMIN_USER_ID = UUID.fromString("28856dce-4f9b-4850-b26a-4126b29faf32"); // tenantadmin@medscan.org

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        tenantRegistry = new TenantRegistry();
        messagingService = new MessagingNotificationService(clinicalService);
        userRegistry = new SeedUserRegistry();
        grievanceService = new GrievanceService(clinicalService, tenantRegistry, messagingService, userRegistry);
    }

    @Test
    @DisplayName("Soumission d'une réclamation patient avec alerte immédiate transmise à l'administrateur de l'hôpital")
    void testSubmitGrievanceAndDirectAlertToHospitalAdmin() {
        PatientGrievance g = grievanceService.submitGrievance(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                HOSPITAL_OUAGA,
                null,
                "COMPLAINT",
                "CARE_QUALITY",
                2,
                "Explications insuffisantes sur les effets secondaires du traitement",
                "Le médecin a été très expéditif lors de la consultation et n'a pas répondu à mes questions sur les contre-indications."
        );

        assertNotNull(g);
        assertNotNull(g.id());
        assertTrue(g.grievanceCode().startsWith("GRV-2026-"));
        assertEquals("SUBMITTED", g.status());
        assertEquals("HIGH", g.severity()); // Rating <= 2 donne sévérité HIGH
        assertEquals("Centre Hospitalier Universitaire de Ouagadougou", g.hospitalName());

        // 1. L'administrateur de l'hôpital (TENANT_ADMIN) doit avoir reçu l'alerte qualité
        List<InAppNotification> adminNotifs = messagingService.listNotificationsForUser(ADMIN_USER_ID, false);
        assertTrue(adminNotifs.stream().anyMatch(n -> n.title().contains("Alerte Qualité : Réclamation patient")));

        // 2. La patiente doit avoir reçu son accusé de réception
        List<InAppNotification> patientNotifs = messagingService.listNotificationsForUser(PATIENT_USER_ID, false);
        assertTrue(patientNotifs.stream().anyMatch(n -> n.title().contains("Signalement enregistré")));
    }

    @Test
    @DisplayName("Traitement d'une réclamation par l'administrateur hospitalier avec mesure corrective")
    void testHospitalAdminReviewCycle() {
        PatientGrievance g = grievanceService.submitGrievance(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                HOSPITAL_OUAGA,
                null,
                "COMPLAINT",
                "BEHAVIOR_ETIQUETTE",
                1,
                "Comportement désinvolte",
                "Attente injustifiée et accueil froid."
        );
        assertEquals("CRITICAL", g.severity());

        // L'administrateur de l'hôpital examine et traite le dossier
        PatientGrievance reviewed = grievanceService.reviewGrievance(
                g.id(),
                ADMIN_USER_ID,
                "Mamadou Sawadogo (Directeur CHU)",
                "RESOLVED",
                "Entretien conduit avec le médecin référent.",
                "Rappel des chartes d'accueil et d'éthique de l'établissement."
        );

        assertEquals("RESOLVED", reviewed.status());
        assertEquals(ADMIN_USER_ID, reviewed.reviewedByAdminId());
        assertEquals("Mamadou Sawadogo (Directeur CHU)", reviewed.reviewedByAdminName());
        assertNotNull(reviewed.reviewedAt());

        // La patiente reçoit la notification de résolution
        List<InAppNotification> patientNotifs = messagingService.listNotificationsForUser(PATIENT_USER_ID, false);
        assertTrue(patientNotifs.stream().anyMatch(n -> n.title().contains("Réponse de la direction hospitalière")));
    }

    @Test
    @DisplayName("Isolation multi-tenant stricte des réclamations")
    void testMultiTenantIsolationOfGrievances() {
        // L'administrateur du CHU ne doit voir que les réclamations de son hôpital
        List<PatientGrievance> ouagaGrievances = grievanceService.listGrievances(HOSPITAL_OUAGA, false, null, null, null);
        assertFalse(ouagaGrievances.isEmpty());
        assertTrue(ouagaGrievances.stream().allMatch(g -> HOSPITAL_OUAGA.equals(g.tenantId())));

        // Le Super Admin voit tout
        List<PatientGrievance> allGrievances = grievanceService.listGrievances(null, true, null, null, null);
        assertTrue(allGrievances.size() >= ouagaGrievances.size());
    }

    @Test
    @DisplayName("Recherche par code et sérialisation JSON d'une réclamation")
    void testJsonSerialization() {
        Optional<PatientGrievance> seed = grievanceService.findGrievanceByCode("GRV-2026-0001");
        assertTrue(seed.isPresent());

        String json = GrievanceJsonMapper.grievanceToJson(seed.get());
        assertTrue(json.contains("GRV-2026-0001"));
        assertTrue(json.contains("Fatou Ouedraogo"));
        assertTrue(json.contains("DELAY_WAIT_TIME"));
        assertTrue(json.contains("RESOLVED"));
    }
}
