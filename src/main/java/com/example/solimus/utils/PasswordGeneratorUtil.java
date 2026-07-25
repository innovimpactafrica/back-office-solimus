package com.example.solimus.utils;

import java.security.SecureRandom;

/**
 * Génère des mots de passe temporaires aléatoires (ex: identifiants envoyés par email
 * après confirmation d'un paiement) — majuscule, minuscule, chiffre et caractère spécial garantis.
 */
public final class PasswordGeneratorUtil {

    private PasswordGeneratorUtil() {
    }

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%&*";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;
    private static final int PASSWORD_LENGTH = 10;

    public static String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();

        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        password.append(UPPER.charAt(random.nextInt(UPPER.length())));
        password.append(LOWER.charAt(random.nextInt(LOWER.length())));
        password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(random.nextInt(SPECIAL.length())));

        for (int i = password.length(); i < PASSWORD_LENGTH; i++) {
            password.append(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
        }

        // Mélange pour que les 4 premiers caractères ne soient pas toujours dans le même ordre de catégorie
        for (int i = password.length() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = password.charAt(i);
            password.setCharAt(i, password.charAt(j));
            password.setCharAt(j, temp);
        }

        return password.toString();
    }
}