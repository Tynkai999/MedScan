package com.medscan.delivery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;
import com.medscan.security.tenant.Tenant;
import com.medscan.security.tenant.TenantRegistry;

/**
 * Service central du Module Logistique & Acheminement Pharmaceutique (MOD-09).
 * Gère la machine à états de livraison, l'attribution aux livreurs assermentés,
 * et la preuve de délivrance par code OTP cryptographique à 6 chiffres.
 */
public class DeliveryService {



    private final Map<UUID, DeliveryOrder> deliveriesById = new ConcurrentHashMap<>();
    private final Map<String, DeliveryOrder> deliveriesByCode = new ConcurrentHashMap<>();
    private final DeliveryOtpService otpService;
    private final ClinicalService clinicalService;
    private final TenantRegistry tenantRegistry;
    private final AtomicInteger codeSequence = new AtomicInteger(100);

    public DeliveryService(DeliveryOtpService otpService, ClinicalService clinicalService, TenantRegistry tenantRegistry) {
        this.otpService = (otpService != null) ? otpService : new DeliveryOtpService();
        this.clinicalService = clinicalService;
        this.tenantRegistry = (tenantRegistry != null) ? tenantRegistry : new TenantRegistry();
        initSeedDeliveries();
    }

    public DeliveryService(ClinicalService clinicalService, TenantRegistry tenantRegistry) {
        this(new DeliveryOtpService(), clinicalService, tenantRegistry);
    }

    public DeliveryService() {
        this(new DeliveryOtpService(), null, new TenantRegistry());
    }

    // ==========================================
    // MÉTHODES DE GESTION DES COMMANDES
    // ==========================================

    public DeliveryOrder createDelivery(
            String prescriptionCode,
            UUID prescriptionId,
            UUID patientId,
            String patientName,
            String patientPhone,
            String deliveryAddress,
            String destinationCity,
            UUID pharmacyTenantId,
            String pharmacyName,
            String packageDescription,
            String specialInstructions,
            UUID actorId,
            String actorUsername,
            String actorRole,
            UUID actorTenantId
    ) {
        UUID id = UUID.randomUUID();
        String code = "DEL-2026-" + String.format("%04d", codeSequence.incrementAndGet());

        String otpClear = otpService.generateOtpCode();
        String otpSalt = otpService.generateSalt();
        String otpHash = otpService.hashOtp(otpClear, otpSalt);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(24, ChronoUnit.HOURS);

        if (patientId == null) {
            throw new IllegalArgumentException("L'identifiant du patient est obligatoire pour créer une livraison.");
        }
        if (pharmacyTenantId == null) {
            throw new IllegalArgumentException("L'identifiant de la structure pharmaceutique est obligatoire.");
        }

        UUID resolvedLogisticsTenant = tenantRegistry.listAllTenants().stream()
                .filter(t -> "LOGISTICS".equalsIgnoreCase(t.type()))
                .map(Tenant::id)
                .findFirst()
                .orElse(actorTenantId != null ? actorTenantId : pharmacyTenantId);

        String resolvedPharmacyName = (pharmacyName != null && !pharmacyName.isBlank())
                ? pharmacyName
                : tenantRegistry.resolveTenantDisplayName(pharmacyTenantId);

        DeliveryOrder order = new DeliveryOrder(
                id,
                code,
                prescriptionCode,
                prescriptionId,
                patientId,
                (patientName != null && !patientName.isBlank()) ? patientName : "Patient Destinataire",
                (patientPhone != null && !patientPhone.isBlank()) ? patientPhone : "+226 70 00 00 00",
                (deliveryAddress != null && !deliveryAddress.isBlank()) ? deliveryAddress : "Adresse patient",
                (destinationCity != null && !destinationCity.isBlank()) ? destinationCity : "Ouagadougou",
                pharmacyTenantId,
                resolvedPharmacyName,
                resolvedLogisticsTenant,
                null,
                null,
                DeliveryStatus.CREATED,
                (packageDescription != null) ? packageDescription : "Colis scellé pharmaceutique inviolable",
                specialInstructions,
                otpHash,
                otpSalt,
                expiresAt,
                DeliveryOtpService.DEFAULT_MAX_ATTEMPTS,
                otpClear,
                now,
                null,
                null,
                null,
                null,
                null
        );

        deliveriesById.put(id, order);
        deliveriesByCode.put(code, order);

        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "DELIVERY_CREATED", "DeliveryOrder", id.toString(), "SUCCESS",
                "Création mission livraison " + code + " pour patient " + patientName + " (Ordonnance: " + prescriptionCode + ")");

        return order;
    }

    public List<DeliveryOrder> listDeliveries(UUID actorUserId, String actorRole, UUID actorTenantId, UUID patientIdFilter) {
        List<DeliveryOrder> all = new ArrayList<>(deliveriesById.values());
        all.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

        if ("SUPER_ADMIN".equalsIgnoreCase(actorRole) || "AUDITOR".equalsIgnoreCase(actorRole)) {
            return Collections.unmodifiableList(all);
        }

        if ("DELIVERY_AGENT".equalsIgnoreCase(actorRole)) {
            return all.stream()
                    .filter(d -> (d.deliveryAgentId() != null && d.deliveryAgentId().equals(actorUserId))
                            || d.status() == DeliveryStatus.CREATED
                            || (d.logisticsTenantId() != null && d.logisticsTenantId().equals(actorTenantId)))
                    .collect(Collectors.toList());
        }

        if ("PHARMACIST".equalsIgnoreCase(actorRole)) {
            return all.stream()
                    .filter(d -> d.pharmacyTenantId() != null && d.pharmacyTenantId().equals(actorTenantId))
                    .collect(Collectors.toList());
        }

        if ("PATIENT".equalsIgnoreCase(actorRole)) {
            return all.stream()
                    .filter(d -> (patientIdFilter != null && patientIdFilter.equals(d.patientId()))
                            || (actorUserId != null && actorUserId.equals(d.patientId())))
                    .collect(Collectors.toList());
        }

        return Collections.unmodifiableList(all);
    }

    public Optional<DeliveryOrder> findById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(deliveriesById.get(id));
    }

    public Optional<DeliveryOrder> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(deliveriesByCode.get(code.trim().toUpperCase()));
    }

    public DeliveryOrder assignDelivery(
            UUID deliveryId,
            UUID agentId,
            String agentName,
            UUID actorId,
            String actorUsername,
            String actorRole,
            UUID actorTenantId
    ) {
        DeliveryOrder order = deliveriesById.get(deliveryId);
        if (order == null) {
            throw new IllegalArgumentException("Commande de livraison introuvable: " + deliveryId);
        }

        if (order.status() != DeliveryStatus.CREATED && order.status() != DeliveryStatus.ASSIGNED) {
            throw new IllegalStateException("Impossible d'assigner une livraison au statut: " + order.status());
        }

        DeliveryOrder updated = order.withAssignment(agentId, agentName, Instant.now());
        deliveriesById.put(deliveryId, updated);
        deliveriesByCode.put(updated.deliveryCode(), updated);

        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "DELIVERY_ASSIGNED", "DeliveryOrder", deliveryId.toString(), "SUCCESS",
                "Mission " + order.deliveryCode() + " assignée au livreur " + agentName + " (" + agentId + ")");

        return updated;
    }

    public DeliveryOrder pickupDelivery(
            UUID deliveryId,
            UUID agentId,
            UUID actorId,
            String actorUsername,
            String actorRole,
            UUID actorTenantId
    ) {
        DeliveryOrder order = deliveriesById.get(deliveryId);
        if (order == null) {
            throw new IllegalArgumentException("Commande de livraison introuvable: " + deliveryId);
        }

        if (order.status() != DeliveryStatus.ASSIGNED) {
            throw new IllegalStateException("Le colis ne peut être retiré que si la mission est au statut ASSIGNED (actuel: " + order.status() + ")");
        }

        if (order.deliveryAgentId() != null && !order.deliveryAgentId().equals(agentId)) {
            throw new SecurityException("Ce colis est réservé à un autre livreur assigné.");
        }

        Instant now = Instant.now();
        DeliveryOrder pickedUp = order.withPickup(now).withInTransit();
        deliveriesById.put(deliveryId, pickedUp);
        deliveriesByCode.put(pickedUp.deliveryCode(), pickedUp);

        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "DELIVERY_PICKED_UP", "DeliveryOrder", deliveryId.toString(), "SUCCESS",
                "Colis " + order.deliveryCode() + " scellé retiré en pharmacie par le livreur. Passage au statut IN_TRANSIT.");

        return pickedUp;
    }

    public DeliveryOrder completeDeliveryWithOtp(
            UUID deliveryId,
            String submittedOtp,
            String recipientConfirmation,
            UUID agentId,
            UUID actorId,
            String actorUsername,
            String actorRole,
            UUID actorTenantId
    ) {
        DeliveryOrder order = deliveriesById.get(deliveryId);
        if (order == null) {
            throw new IllegalArgumentException("Commande de livraison introuvable: " + deliveryId);
        }

        if (order.status() != DeliveryStatus.IN_TRANSIT && order.status() != DeliveryStatus.PICKED_UP) {
            throw new IllegalStateException("La livraison ne peut être finalisée que si elle est en cours d'acheminement (IN_TRANSIT). Statut actuel: " + order.status());
        }

        if (order.otpAttemptsRemaining() <= 0) {
            throw new IllegalStateException("Nombre maximal d'essais OTP dépassé. Cette livraison est bloquée par sécurité.");
        }

        if (Instant.now().isAfter(order.otpExpiresAt())) {
            DeliveryOrder expired = order.withFailure("Code OTP expiré (dépassement du délai de 24h).");
            deliveriesById.put(deliveryId, expired);
            deliveriesByCode.put(expired.deliveryCode(), expired);
            throw new IllegalStateException("Le code OTP de livraison a expiré.");
        }

        boolean isValid = otpService.verifyOtp(submittedOtp, order.otpHash(), order.otpSalt());
        if (!isValid) {
            DeliveryOrder decremented = order.withDecrementedAttempts();
            deliveriesById.put(deliveryId, decremented);
            deliveriesByCode.put(decremented.deliveryCode(), decremented);

            logAudit(actorId, actorUsername, actorRole, actorTenantId,
                    "DELIVERY_OTP_FAILED", "DeliveryOrder", deliveryId.toString(), "FAILURE",
                    "Tentative OTP erronée pour la livraison " + order.deliveryCode() + ". Essais restants: " + decremented.otpAttemptsRemaining());

            throw new IllegalArgumentException("Code OTP invalide. Tentatives restantes : " + decremented.otpAttemptsRemaining());
        }

        // Succès : validation de la remise sécurisée
        Instant now = Instant.now();
        String signature = (recipientConfirmation != null && !recipientConfirmation.isBlank()) ? recipientConfirmation : order.patientName();
        DeliveryOrder completed = order.withDeliverySuccess(now, signature);
        deliveriesById.put(deliveryId, completed);
        deliveriesByCode.put(completed.deliveryCode(), completed);

        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "DELIVERY_COMPLETED_WITH_OTP", "DeliveryOrder", deliveryId.toString(), "SUCCESS",
                "Livraison " + order.deliveryCode() + " validée avec succès par OTP. Remis en main propre à " + signature);

        return completed;
    }

    public DeliveryOrder failDelivery(
            UUID deliveryId,
            String reason,
            UUID actorId,
            String actorUsername,
            String actorRole,
            UUID actorTenantId
    ) {
        DeliveryOrder order = deliveriesById.get(deliveryId);
        if (order == null) {
            throw new IllegalArgumentException("Commande de livraison introuvable: " + deliveryId);
        }

        DeliveryOrder failed = order.withFailure(reason);
        deliveriesById.put(deliveryId, failed);
        deliveriesByCode.put(failed.deliveryCode(), failed);

        logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "DELIVERY_FAILED", "DeliveryOrder", deliveryId.toString(), "WARNING",
                "Échec signalé pour la livraison " + order.deliveryCode() + ". Motif: " + reason);

        return failed;
    }

    public String getOtpForPatient(UUID deliveryId, UUID requestingUserId, String actorRole) {
        DeliveryOrder order = deliveriesById.get(deliveryId);
        if (order == null) {
            throw new IllegalArgumentException("Commande de livraison introuvable: " + deliveryId);
        }

        if ("DELIVERY_AGENT".equalsIgnoreCase(actorRole)) {
            throw new SecurityException("Accès formellement interdit : le livreur ne peut pas consulter le code secret OTP du patient.");
        }

        // Patient ou administrateur
        if (order.status() == DeliveryStatus.DELIVERED) {
            throw new IllegalStateException("La livraison est déjà terminée, le code OTP a été détruit.");
        }

        return order.otpClear();
    }

    public long countActiveDeliveriesForPatient(UUID patientId) {
        if (patientId == null) return 0;
        return deliveriesById.values().stream()
                .filter(d -> patientId.equals(d.patientId()))
                .filter(d -> d.status() != DeliveryStatus.DELIVERED && d.status() != DeliveryStatus.CANCELLED && d.status() != DeliveryStatus.FAILED)
                .count();
    }

    public long countDeliveriesByAgentAndStatus(UUID agentId, DeliveryStatus status) {
        if (agentId == null) return 0;
        return deliveriesById.values().stream()
                .filter(d -> agentId.equals(d.deliveryAgentId()))
                .filter(d -> d.status() == status)
                .count();
    }

    public long countDeliveriesByPharmacyAndStatus(UUID pharmacyTenantId, DeliveryStatus status) {
        if (pharmacyTenantId == null) return 0;
        return deliveriesById.values().stream()
                .filter(d -> pharmacyTenantId.equals(d.pharmacyTenantId()))
                .filter(d -> d.status() == status)
                .count();
    }

    private void logAudit(UUID actorId, String actorUsername, String actorRole, UUID tenantId,
                          String action, String resourceType, String resourceId, String outcome, String details) {
        if (clinicalService != null) {
            clinicalService.logAudit(actorId, actorUsername, actorRole, tenantId, action, resourceType, resourceId, outcome, details);
        }
    }

    // ==========================================
    // DONNÉES DE DÉMONSTRATION (SEEDS RÉALISTES)
    // ==========================================

    private void initSeedDeliveries() {
        UUID seedPharmacyTenantId = UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89");
        UUID seedLogisticsTenantId = UUID.fromString("ddfd4bda-a3a7-401a-a701-3ef502072705");
        UUID pFatouId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
        UUID courierId = UUID.fromString("295a0bc4-ff23-455b-80df-8b21c43d9241"); // Ibrahim Zongo

        // 1. Mission active en cours d'acheminement (IN_TRANSIT)
        UUID d1Id = UUID.fromString("87a54cf8-5e82-4df9-a9a3-5c26b5d7e012");
        String d1Code = "DEL-2026-0089";
        String otp1 = "482910";
        String salt1 = otpService.generateSalt();
        String hash1 = otpService.hashOtp(otp1, salt1);

        DeliveryOrder d1 = new DeliveryOrder(
                d1Id,
                d1Code,
                "RX-2026-0042",
                null,
                pFatouId,
                "Fatou Ouedraogo",
                "+226 70 12 34 56",
                "Quartier Ouaga 2000, Villa 412, Rue des Ambassades",
                "Ouagadougou",
                seedPharmacyTenantId,
                "Grande Pharmacie Centrale & Officine Avenir",
                seedLogisticsTenantId,
                courierId,
                "Ibrahim Zongo",
                DeliveryStatus.IN_TRANSIT,
                "Colis scellé pharmaceutique inviolable #SC-4491 (Traitement Asthme)",
                "Conserver à l'abri de la chaleur. Valider impérativement par OTP avant remise.",
                hash1,
                salt1,
                Instant.now().plus(20, ChronoUnit.HOURS),
                3,
                otp1,
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(90, ChronoUnit.MINUTES),
                Instant.now().minus(30, ChronoUnit.MINUTES),
                null,
                null,
                null
        );
        deliveriesById.put(d1Id, d1);
        deliveriesByCode.put(d1Code, d1);

        // 2. Mission créée en attente de livreur (CREATED)
        UUID d2Id = UUID.fromString("a1f940b3-90ce-4c12-8874-297c88bba104");
        String d2Code = "DEL-2026-0105";
        String otp2 = "739154";
        String salt2 = otpService.generateSalt();
        String hash2 = otpService.hashOtp(otp2, salt2);

        DeliveryOrder d2 = new DeliveryOrder(
                d2Id,
                d2Code,
                "RX-2026-0078",
                null,
                pFatouId,
                "Fatou Ouedraogo",
                "+226 70 12 34 56",
                "Quartier Ouaga 2000, Villa 412, Rue des Ambassades",
                "Ouagadougou",
                seedPharmacyTenantId,
                "Grande Pharmacie Centrale & Officine Avenir",
                seedLogisticsTenantId,
                null,
                null,
                DeliveryStatus.CREATED,
                "Colis scellé pharmaceutique inviolable #SC-4502 (Antibiotiques pédiatriques)",
                "Colis prêt au comptoir B de l'officine.",
                hash2,
                salt2,
                Instant.now().plus(23, ChronoUnit.HOURS),
                3,
                otp2,
                Instant.now().minus(40, ChronoUnit.MINUTES),
                null,
                null,
                null,
                null,
                null
        );
        deliveriesById.put(d2Id, d2);
        deliveriesByCode.put(d2Code, d2);

        // 3. Mission livrée plus tôt avec succès (DELIVERED)
        UUID d3Id = UUID.fromString("f49a37bc-7390-4821-bc76-0274bb9aa812");
        String d3Code = "DEL-2026-0041";
        DeliveryOrder d3 = new DeliveryOrder(
                d3Id,
                d3Code,
                "RX-2026-0019",
                null,
                pFatouId,
                "Fatou Ouedraogo",
                "+226 70 12 34 56",
                "Quartier Ouaga 2000, Villa 412, Rue des Ambassades",
                "Ouagadougou",
                seedPharmacyTenantId,
                "Grande Pharmacie Centrale & Officine Avenir",
                seedLogisticsTenantId,
                courierId,
                "Ibrahim Zongo",
                DeliveryStatus.DELIVERED,
                "Colis scellé pharmaceutique inviolable #SC-4389",
                "Remis au domicile.",
                "hash_completed",
                "salt_completed",
                Instant.now().minus(1, ChronoUnit.DAYS),
                0,
                null,
                Instant.now().minus(26, ChronoUnit.HOURS),
                Instant.now().minus(25, ChronoUnit.HOURS),
                Instant.now().minus(24, ChronoUnit.HOURS),
                Instant.now().minus(23, ChronoUnit.HOURS),
                "Fatou Ouedraogo (Signature numérique validée)",
                null
        );
        deliveriesById.put(d3Id, d3);
        deliveriesByCode.put(d3Code, d3);
    }
}
