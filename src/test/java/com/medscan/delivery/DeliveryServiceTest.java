package com.medscan.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.clinical.ClinicalService;
import com.medscan.security.tenant.TenantRegistry;

class DeliveryServiceTest {

    private DeliveryService deliveryService;
    private ClinicalService clinicalService;
    private TenantRegistry tenantRegistry;

    private static final UUID PATIENT_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
    private static final UUID PHARMACY_TENANT = UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89");
    private static final UUID COURIER_ID = UUID.fromString("295a0bc4-ff23-455b-80df-8b21c43d9241");
    private static final UUID COURIER_TENANT = UUID.fromString("ddfd4bda-a3a7-401a-a701-3ef502072705");

    @BeforeEach
    void setUp() {
        tenantRegistry = new TenantRegistry();
        clinicalService = new ClinicalService();
        deliveryService = new DeliveryService(clinicalService, tenantRegistry);
    }

    @Test
    @DisplayName("Génération et vérification OTP nominale à 6 chiffres")
    void testOtpServiceNominal() {
        DeliveryOtpService otpService = new DeliveryOtpService();
        String code = otpService.generateOtpCode();
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));

        String salt = otpService.generateSalt();
        assertNotNull(salt);
        String hash = otpService.hashOtp(code, salt);
        assertNotNull(hash);

        assertTrue(otpService.verifyOtp(code, hash, salt));
        assertFalse(otpService.verifyOtp("000000", hash, salt));
        assertFalse(otpService.verifyOtp(null, hash, salt));
    }

    @Test
    @DisplayName("Cycle de vie complet d'une livraison sécurisée avec validation OTP")
    void testFullDeliveryLifecycle() {
        // 1. Création de la mission par l'officine
        DeliveryOrder created = deliveryService.createDelivery(
                "RX-2026-9999",
                UUID.randomUUID(),
                PATIENT_ID,
                "Fatou Ouedraogo",
                "+226 70 12 34 56",
                "Ouaga 2000, Rue 14",
                "Ouagadougou",
                PHARMACY_TENANT,
                "Grande Pharmacie Centrale",
                "Colis thermosensible (Insuline)",
                "Respecter la chaîne du froid",
                UUID.randomUUID(), "pharmacist@medscan.org", "PHARMACIST", PHARMACY_TENANT
        );

        assertNotNull(created);
        assertEquals(DeliveryStatus.CREATED, created.status());
        assertTrue(created.deliveryCode().startsWith("DEL-2026-"));
        assertNotNull(created.otpClear());
        assertEquals(3, created.otpAttemptsRemaining());

        String secretOtp = created.otpClear();

        // 2. Attribution au livreur
        DeliveryOrder assigned = deliveryService.assignDelivery(
                created.id(),
                COURIER_ID,
                "Ibrahim Zongo",
                COURIER_ID, "delivery@medscan.org", "DELIVERY_AGENT", COURIER_TENANT
        );
        assertEquals(DeliveryStatus.ASSIGNED, assigned.status());
        assertEquals(COURIER_ID, assigned.deliveryAgentId());

        // 3. Retrait du colis scellé en officine
        DeliveryOrder pickedUp = deliveryService.pickupDelivery(
                created.id(),
                COURIER_ID,
                COURIER_ID, "delivery@medscan.org", "DELIVERY_AGENT", COURIER_TENANT
        );
        assertEquals(DeliveryStatus.IN_TRANSIT, pickedUp.status());
        assertNotNull(pickedUp.pickedUpAt());

        // 4. Finalisation de la remise avec le bon code OTP fourni par le patient
        DeliveryOrder completed = deliveryService.completeDeliveryWithOtp(
                created.id(),
                secretOtp,
                "Fatou Ouedraogo (Destinataire en personne)",
                COURIER_ID,
                COURIER_ID, "delivery@medscan.org", "DELIVERY_AGENT", COURIER_TENANT
        );

        assertEquals(DeliveryStatus.DELIVERED, completed.status());
        assertNotNull(completed.deliveredAt());
        assertNull(completed.otpClear(), "Le code OTP clair doit être effacé une fois la livraison validée !");
        assertEquals("Fatou Ouedraogo (Destinataire en personne)", completed.recipientConfirmation());
    }

    @Test
    @DisplayName("Saisie d'un code OTP erroné décrémente les tentatives et bloque à 0")
    void testBadOtpDecrementsAttemptsAndFails() {
        DeliveryOrder order = deliveryService.createDelivery(
                "RX-2026-8888", null, PATIENT_ID, "Patient Test", "+226 70 00 00 00",
                "Adresse", "Ouagadougou", PHARMACY_TENANT, "Pharmacie",
                "Colis", null, UUID.randomUUID(), "pharma", "PHARMACIST", PHARMACY_TENANT
        );

        deliveryService.assignDelivery(order.id(), COURIER_ID, "Livreur", COURIER_ID, "courier", "DELIVERY_AGENT", COURIER_TENANT);
        deliveryService.pickupDelivery(order.id(), COURIER_ID, COURIER_ID, "courier", "DELIVERY_AGENT", COURIER_TENANT);

        // Tentative 1 erronée
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                deliveryService.completeDeliveryWithOtp(order.id(), "000000", "Sign", COURIER_ID, COURIER_ID, "c", "DELIVERY_AGENT", COURIER_TENANT)
        );
        assertTrue(ex1.getMessage().contains("2"));

        // Tentative 2 erronée
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                deliveryService.completeDeliveryWithOtp(order.id(), "111111", "Sign", COURIER_ID, COURIER_ID, "c", "DELIVERY_AGENT", COURIER_TENANT)
        );
        assertTrue(ex2.getMessage().contains("1"));

        // Tentative 3 erronée -> verrouillage et échec
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () ->
                deliveryService.completeDeliveryWithOtp(order.id(), "222222", "Sign", COURIER_ID, COURIER_ID, "c", "DELIVERY_AGENT", COURIER_TENANT)
        );
        assertTrue(ex3.getMessage().contains("0"));

        DeliveryOrder locked = deliveryService.findById(order.id()).orElseThrow();
        assertEquals(DeliveryStatus.FAILED, locked.status());
        assertEquals(0, locked.otpAttemptsRemaining());
    }

    @Test
    @DisplayName("Interdiction stricte : le livreur ne peut pas consulter le code secret OTP")
    void testCourierCannotConsultOtp() {
        DeliveryOrder order = deliveryService.createDelivery(
                "RX-2026-7777", null, PATIENT_ID, "Patient Test", "+226 70 00 00 00",
                "Adresse", "Ouagadougou", PHARMACY_TENANT, "Pharmacie",
                "Colis", null, UUID.randomUUID(), "pharma", "PHARMACIST", PHARMACY_TENANT
        );

        // Livreur tente de consulter l'OTP
        assertThrows(SecurityException.class, () ->
                deliveryService.getOtpForPatient(order.id(), COURIER_ID, "DELIVERY_AGENT")
        );

        // Patient consulte son propre code OTP
        String otp = deliveryService.getOtpForPatient(order.id(), PATIENT_ID, "PATIENT");
        assertNotNull(otp);
        assertEquals(6, otp.length());
    }

    @Test
    @DisplayName("Filtrage des livraisons selon le rôle et le tenant")
    void testListDeliveriesRoleFiltering() {
        // Livreur
        List<DeliveryOrder> courierList = deliveryService.listDeliveries(COURIER_ID, "DELIVERY_AGENT", COURIER_TENANT, null);
        assertNotNull(courierList);
        assertTrue(courierList.size() >= 2);

        // Pharmacien
        List<DeliveryOrder> pharmaList = deliveryService.listDeliveries(UUID.randomUUID(), "PHARMACIST", PHARMACY_TENANT, null);
        assertNotNull(pharmaList);
        assertTrue(pharmaList.stream().allMatch(d -> PHARMACY_TENANT.equals(d.pharmacyTenantId())));

        // Super Admin
        List<DeliveryOrder> adminList = deliveryService.listDeliveries(UUID.randomUUID(), "SUPER_ADMIN", UUID.randomUUID(), null);
        assertTrue(adminList.size() >= 3);
    }
}
