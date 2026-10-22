package com.medscan.delivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Service cryptographique pour la génération et la vérification des codes OTP (MOD-09).
 * Utilise un SecureRandom pour générer des codes numériques à 6 chiffres,
 * et SHA-256 avec sel aléatoire pour la vérification à temps constant.
 */
public class DeliveryOtpService {

    private static final int OTP_DIGITS = 6;
    private static final int SALT_BYTES = 16;
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final SecureRandom secureRandom;

    public DeliveryOtpService() {
        SecureRandom sr;
        try {
            sr = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            sr = new SecureRandom();
        }
        this.secureRandom = sr;
    }

    /**
     * Génère un code OTP aléatoire de 6 chiffres (000000 à 999999).
     */
    public String generateOtpCode() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%0" + OTP_DIGITS + "d", number);
    }

    /**
     * Génère un sel cryptographique aléatoire de 16 octets sous forme hexadécimale.
     */
    public String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Calcule l'empreinte SHA-256 du code OTP salé.
     */
    public String hashOtp(String otpCode, String salt) {
        if (otpCode == null || salt == null) {
            throw new IllegalArgumentException("Le code OTP et le sel sont obligatoires.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = salt + ":" + otpCode.trim();
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible.", e);
        }
    }

    /**
     * Vérifie si le code OTP saisi correspond au condensat stocké (comparaison à temps constant).
     */
    public boolean verifyOtp(String enteredOtp, String storedHash, String storedSalt) {
        if (enteredOtp == null || storedHash == null || storedSalt == null) {
            return false;
        }
        String computedHash = hashOtp(enteredOtp, storedSalt);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
