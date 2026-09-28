package com.medscan.security.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Représente un établissement de santé ou organisation partenaire (MOD-01 Multi-Tenancy).
 * Ex: Hôpital, Clinique, Pharmacie, Laboratoire, Cabinet médical, Plateforme centrale.
 */
public record Tenant(
        UUID id,
        String code,
        String name,
        String type,        // HOSPITAL, CLINIC, PHARMACY, LABORATORY, CABINET, LOGISTICS, PLATFORM
        String country,
        String city,
        String phone,
        String email,
        String address,
        String status,      // ACTIVE, SUSPENDED, PENDING
        Instant createdAt
) {
    public Tenant {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        type = (type == null || type.isBlank()) ? "CLINIC" : type.trim().toUpperCase();
        country = (country == null || country.isBlank()) ? "Burkina Faso" : country.trim();
        city = (city == null || city.isBlank()) ? "Ouagadougou" : city.trim();
        phone = (phone == null) ? "" : phone.trim();
        email = (email == null) ? "" : email.trim();
        address = (address == null) ? "" : address.trim();
        status = (status == null || status.isBlank()) ? "ACTIVE" : status.trim().toUpperCase();
        createdAt = (createdAt == null) ? Instant.now() : createdAt;
    }
}
