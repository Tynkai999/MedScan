package com.medscan.referral;

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
import com.medscan.security.tenant.TenantRegistry;

class ReferralServiceTest {

    private ClinicalService clinicalService;
    private TenantRegistry tenantRegistry;
    private MessagingNotificationService messagingService;
    private ReferralService referralService;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"); // Fatou Ouedraogo
    private static final UUID PATIENT_USER_ID = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4");
    private static final UUID DOCTOR_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");  // Dr Seydou Traore
    private static final UUID HOSPITAL_OUAGA = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID CLINIC_ALBIES = UUID.fromString("f56de30f-c802-42c6-8587-707a8d1a9814");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        tenantRegistry = new TenantRegistry();
        messagingService = new MessagingNotificationService(clinicalService);
        referralService = new ReferralService(clinicalService, tenantRegistry, messagingService);
    }

    @Test
    @DisplayName("Création d'une orientation inter-services avec scellage numérique et notification patient")
    void testCreateInterServiceReferral() {
        MedicalReferral ref = referralService.createReferral(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HOSPITAL_OUAGA,
                "Médecine Interne",
                HOSPITAL_OUAGA,
                "Cardiologie & Explorations Fonctionnelles",
                null,
                null,
                "Bilan d'hypertension artérielle résistante avec dyspnée d'effort",
                "URGENT",
                "Patiente sous traitement de palier 2. Suspicion de cardiopathie hypertensive."
        );

        assertNotNull(ref);
        assertNotNull(ref.id());
        assertTrue(ref.referralCode().startsWith("REF-2026-"));
        assertEquals("PENDING", ref.status());
        assertEquals("Cardiologie & Explorations Fonctionnelles", ref.targetDepartment());
        assertEquals("URGENT", ref.priority());

        // Vérification du sceau de l'hôpital
        assertNotNull(ref.hospitalSeal());
        assertEquals("CH_OUAGADOUGOU", ref.hospitalSeal().hospitalCode());
        assertNotNull(ref.hospitalSeal().verificationHash());

        // Vérification de la notification automatique reçue par la patiente
        List<InAppNotification> notifs = messagingService.listNotificationsForUser(PATIENT_USER_ID, false);
        assertTrue(notifs.stream().anyMatch(n -> n.title().contains("orientation médicale")));
    }

    @Test
    @DisplayName("Orientation médicale inter-établissements (CHU de Ouaga vers Clinique des Albies)")
    void testCreateInterHospitalReferral() {
        MedicalReferral ref = referralService.createReferral(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HOSPITAL_OUAGA,
                "Urgences",
                CLINIC_ALBIES,
                "Chirurgie Viscérale",
                null,
                "Dr. Aïssatou Sawadogo",
                "Suspicion appendicite aiguë atypique, demande d'avis chirurgical immédiat",
                "EMERGENCY",
                "Douleurs fosse iliaque droite depuis 36h, défense localisée, hyperleucocytose."
        );

        assertEquals(HOSPITAL_OUAGA, ref.sourceTenantId());
        assertEquals(CLINIC_ALBIES, ref.targetTenantId());
        assertEquals("Clinique Médico-Chirurgicale des Albies", ref.targetTenantName());
        assertEquals("EMERGENCY", ref.priority());
    }

    @Test
    @DisplayName("Cycle de vie d'une orientation : Acceptation puis réalisation complète")
    void testAcceptAndCompleteReferralCycle() {
        MedicalReferral ref = referralService.createReferral(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HOSPITAL_OUAGA,
                "Consultation Générale",
                HOSPITAL_OUAGA,
                "Pneumologie",
                null,
                null,
                "Asthme sévère non contrôlé",
                "ROUTINE",
                "EFR perturbées"
        );

        // 1. Acceptation par le service cible
        MedicalReferral accepted = referralService.updateReferralStatus(
                ref.id(), "ACCEPTED", null, DOCTOR_ID, "Dr. Traore", "DOCTOR", HOSPITAL_OUAGA
        );
        assertEquals("ACCEPTED", accepted.status());

        // 2. Réalisation de la consultation de redirection
        MedicalReferral completed = referralService.updateReferralStatus(
                ref.id(), "COMPLETED", null, DOCTOR_ID, "Dr. Traore", "DOCTOR", HOSPITAL_OUAGA
        );
        assertEquals("COMPLETED", completed.status());
    }

    @Test
    @DisplayName("Rejet motivé d'une orientation médicale")
    void testRejectReferralWithReason() {
        MedicalReferral ref = referralService.createReferral(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HOSPITAL_OUAGA,
                "Consultation",
                HOSPITAL_OUAGA,
                "Dermatologie",
                null,
                null,
                "Lésion cutanée suspecte",
                "ROUTINE",
                "Prurit et érythème"
        );

        MedicalReferral rejected = referralService.updateReferralStatus(
                ref.id(), "REJECTED", "Plateau technique indisponible, réorienter vers le Centre National de Dermatologie",
                DOCTOR_ID, "Dr. Traore", "DOCTOR", HOSPITAL_OUAGA
        );

        assertEquals("REJECTED", rejected.status());
        assertEquals("Plateau technique indisponible, réorienter vers le Centre National de Dermatologie", rejected.rejectionReason());
    }

    @Test
    @DisplayName("Sérialisation JSON et consultation par code")
    void testJsonSerializationAndCodeLookup() {
        Optional<MedicalReferral> seedOpt = referralService.findReferralByCode("REF-2026-0001");
        assertTrue(seedOpt.isPresent());

        String json = ReferralJsonMapper.referralToJson(seedOpt.get());
        assertTrue(json.contains("REF-2026-0001"));
        assertTrue(json.contains("Fatou Ouedraogo"));
        assertTrue(json.contains("CH_OUAGADOUGOU"));
        assertFalse(json.contains("\"null\""));
    }
}
