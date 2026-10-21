package com.example.solimus.utils;

import java.security.SecureRandom;

/**
 * Génère des mots de passe temporaires aléatoires (ex: identifiants envoyés par email
 * après confirmation d'un paiement) — majuscule, minuscule, chiffre et caractère spécial garantis.
 */
public final class PasswordGeneratorUtil {

    private PasswordGeneratorUtil() {
    }

    // Sans caractères ambigus à la lecture d'un email (I / l / 1, O / 0) : l'utilisateur recopie le
    // mot de passe temporaire à la main — un "I" lu comme "l" donnait "Identifiants incorrects"
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%*";
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