package com.medscan.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.medscan.security.jwt.JwtTokenService;
import com.medscan.security.tenant.TenantRegistry;

class UserRegistryTest {

    private SeedUserRegistry userRegistry;
    private AuthService authService;
    private final UUID hospitalTenantId = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");

    @BeforeEach
    void setUp() {
        userRegistry = new SeedUserRegistry();
        authService = new AuthService(userRegistry, new JwtTokenService());
    }

    @Test
    @DisplayName("Les 10 acteurs seed sont enregistrés et trouvables par identifiant et par tenant")
    void testSeedActors() {
        assertEquals(10, userRegistry.allActors().size());

        List<ActorAccount> hospitalStaff = userRegistry.findByTenantId(hospitalTenantId);
        // Doit inclure au moins patient, doctor, radiologist, nurse, tenantadmin
        assertTrue(hospitalStaff.size() >= 5);
        assertTrue(hospitalStaff.stream().anyMatch(u -> "doctor@medscan.org".equals(u.username())));
        assertTrue(hospitalStaff.stream().anyMatch(u -> "nurse@medscan.org".equals(u.username())));
    }

    @Test
    @DisplayName("Création dynamique d'un nouveau médecin par l'administrateur d'établissement")
    void testCreateDoctorDynamically() {
        String newDocUsername = "dr.sawadogo@ch-ouaga.bf";
        ActorAccount newDoc = userRegistry.createStaffUser(
                newDocUsername,
                newDocUsername,
                "SecretDoc2026!",
                "Dr. Alassane Sawadogo (Pneumologue)",
                "DOCTOR",
                hospitalTenantId,
                "CH_OUAGADOUGOU"
        );

        assertNotNull(newDoc);
        assertNotNull(newDoc.userId());
        assertEquals(newDocUsername, newDoc.username());
        assertTrue(newDoc.roles().contains("DOCTOR"));
        assertTrue(newDoc.permissions().contains("consultation:write"));
        assertTrue(newDoc.permissions().contains("prescription:create"));

        // Vérification dans le registre
        assertTrue(userRegistry.userExists(newDocUsername));
        Optional<ActorAccount> retrieved = userRegistry.findByUsername(newDocUsername);
        assertTrue(retrieved.isPresent());
        assertEquals("Dr. Alassane Sawadogo (Pneumologue)", retrieved.get().displayName());

        // Le nouveau médecin peut immédiatement s'authentifier via AuthService !
        AuthResult auth = authService.login(newDocUsername, "SecretDoc2026!");
        assertNotNull(auth.accessToken());
        assertNotNull(auth.refreshToken());
        assertEquals(newDoc.userId(), auth.account().userId());
        assertEquals("DOCTOR", auth.account().roles().iterator().next());
    }

    @Test
    @DisplayName("Création d'un infirmier avec permissions adaptées et validation des doublons")
    void testCreateNurseAndDuplicateValidation() {
        String nurseUsername = "inf.zongo@ch-ouaga.bf";
        ActorAccount nurse = userRegistry.createStaffUser(
                nurseUsername,
                nurseUsername,
                "NursePass123!",
                "Inf. Salimata Zongo",
                "NURSE",
                hospitalTenantId,
                "CH_OUAGADOUGOU"
        );

        assertNotNull(nurse);
        assertTrue(nurse.roles().contains("NURSE"));
        assertTrue(nurse.permissions().contains("vitals:record"));

        // Tentative d'ajouter le même identifiant doit échouer
        assertThrows(IllegalArgumentException.class, () -> {
            userRegistry.createStaffUser(nurseUsername, "autre@mail.bf", "Pass123!", "Doublon", "NURSE", hospitalTenantId, "CH");
        });
    }
}
