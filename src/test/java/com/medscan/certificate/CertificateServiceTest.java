package com.medscan.certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;
import com.medscan.messaging.InAppNotification;
import com.medscan.messaging.MessagingNotificationService;
import com.medscan.security.tenant.TenantRegistry;

class CertificateServiceTest {

    private ClinicalService clinicalService;
    private TenantRegistry tenantRegistry;
    private MessagingNotificationService messagingService;
    private CertificateService certificateService;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"); // Fatou Ouedraogo
    private static final UUID PATIENT_USER_ID = UUID.fromString("116286b8-79e3-48b6-b99e-06fed5f10ee4");
    private static final UUID DOCTOR_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");  // Dr Seydou Traore
    private static final UUID HOSPITAL_OUAGA = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");

    @BeforeEach
    void setUp() {
        clinicalService = new ClinicalService();
        tenantRegistry = new TenantRegistry();
        messagingService = new MessagingNotificationService(clinicalService);
        certificateService = new CertificateService(clinicalService, tenantRegistry, messagingService);
    }

    @Test
    @DisplayName("Émission d'un arrêt de travail pour employeur avec cachet officiel de l'hôpital")
    void testIssueSickLeaveCertificateWithHospitalSeal() {
        MedicalCertificate cert = certificateService.issueCertificate(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HOSPITAL_OUAGA,
                "SICK_LEAVE",
                "Infection paludéenne fébrile aiguë nécessitant éviction et repos strict",
                Instant.now(),
                5,
                "EMPLOYER",
                "Société Nationale d'Électricité du Burkina (SONABEL) - Direction RH",
                "Repos strict au lit, interdiction de port de charges lourdes"
        );

        assertNotNull(cert);
        assertNotNull(cert.id());
        assertTrue(cert.certificateCode().startsWith("CERT-2026-"));
        assertEquals("SICK_LEAVE", cert.certificateType());
        assertEquals(5, cert.durationDays());
        assertEquals("ISSUED", cert.status());

        // Vérification du cachet numérique
        HospitalSeal seal = cert.hospitalSeal();
        assertNotNull(seal);
        assertEquals(HOSPITAL_OUAGA, seal.tenantId());
        assertEquals("CH_OUAGADOUGOU", seal.hospitalCode());
        assertNotNull(seal.verificationHash());
        assertNotNull(seal.verificationUrl());

        // Alerte automatique reçue par la patiente pour téléchargement
        List<InAppNotification> notifs = messagingService.listNotificationsForUser(PATIENT_USER_ID, false);
        assertTrue(notifs.stream().anyMatch(n -> n.title().contains("Justificatif médical")));
    }

    @Test
    @DisplayName("Émission d'une dispense médicale étudiante / universitaire")
    void testIssueStudentDispenseCertificate() {
        MedicalCertificate cert = certificateService.issueCertificate(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HOSPITAL_OUAGA,
                "STUDENT_DISPENSE",
                "Crise d'asthme aiguë avec détresse respiratoire modérée",
                Instant.now(),
                3,
                "UNIVERSITY_SCHOOL",
                "Université Joseph Ki-Zerbo - UFR Sciences de la Santé",
                "Dispense complète de cours magistraux, travaux pratiques et épreuves sportives"
        );

        assertEquals("STUDENT_DISPENSE", cert.certificateType());
        assertEquals("UNIVERSITY_SCHOOL", cert.recipientType());
        assertEquals(3, cert.durationDays());
    }

    @Test
    @DisplayName("Vérification publique de validité par code et hash cryptographique")
    void testPublicVerificationOfCertificate() {
        MedicalCertificate cert = certificateService.issueCertificate(
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HOSPITAL_OUAGA,
                "ATTENDANCE_PROOF",
                "Consultation médicale spécialisée",
                Instant.now(),
                1,
                "GENERAL",
                "À qui de droit",
                "Présence effective en consultation de 09h00 à 11h30"
        );

        String code = cert.certificateCode();
        String hash = cert.hospitalSeal().verificationHash();

        // 1. Hash et code valides -> VRAI
        assertTrue(certificateService.verifyCertificate(code, hash));

        // 2. Faux hash -> FAUX
        assertFalse(certificateService.verifyCertificate(code, "FAKE_TAMPERED_HASH_12345"));

        // 3. Faux code -> FAUX
        assertFalse(certificateService.verifyCertificate("CERT-FAKE-9999", hash));
    }

    @Test
    @DisplayName("Génération du document officiel HTML prêt pour impression avec cachet d'hôpital")
    void testRenderOfficialPrintableHtmlDocument() {
        MedicalCertificate cert = certificateService.findCertificateByCode("CERT-2026-0042")
                .orElseThrow();

        String html = certificateService.renderCertificateHtml(cert.id());
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("CERTIFICAT MÉDICAL D'ARRÊT DE TRAVAIL"));
        assertTrue(html.contains("Fatou Ouedraogo"));
        assertTrue(html.contains("Dr. Seydou Traore"));
        assertTrue(html.contains("Centre Hospitalier Universitaire de Ouagadougou"));
        assertTrue(html.contains("CACHET OFFICIEL"));
        assertTrue(html.contains("window.print()"));
        assertTrue(html.contains("EMPREINTE DE VÉRIFICATION SHA-256"));
    }

    @Test
    @DisplayName("Sérialisation JSON d'un certificat et de la liste")
    void testJsonSerialization() {
        List<MedicalCertificate> list = certificateService.listCertificates(HOSPITAL_OUAGA, PATIENT_ID, null);
        assertFalse(list.isEmpty());

        String json = CertificateJsonMapper.listToJson(list);
        assertTrue(json.contains("CERT-2026-0042"));
        assertTrue(json.contains("CH_OUAGADOUGOU"));
    }
}
