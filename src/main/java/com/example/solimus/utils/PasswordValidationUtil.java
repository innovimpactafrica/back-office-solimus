package com.example.solimus.utils;

import com.example.solimus.exceptions.BadRequestException;

/**
 * Règle centralisée de complexité des mots de passe — source de vérité unique.
 *
 * Règle : minimum 8 caractères, au moins une majuscule, une minuscule,
 * un chiffre et un caractère spécial.
 *
 * Utilisée par :
 *  - les annotations @Pattern des DTO (via les constantes PASSWORD_REGEX / PASSWORD_MESSAGE)
 *  - les services au moment où un mot de passe est défini ou modifié
 *    (inscription, activation, réinitialisation, changement depuis le profil)
 */
public final class PasswordValidationUtil {

    /** Longueur minimale. */
    public static final int MIN_LENGTH = 8;

    /**
     * 1 minuscule + 1 majuscule + 1 chiffre + 1 caractère spécial
     * (caractère non alphanumérique et non espace) sur au moins 8 caractères.
     */
    public static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\p{L}\\p{N}\\s]).{8,}$";

    /** Message d'erreur standardisé (utilisable aussi dans les annotations @Pattern). */
    public static final String PASSWORD_MESSAGE =
            "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, "
                    + "un chiffre et un caractère spécial";

    private PasswordValidationUtil() {
    }

    /** Vérifie la règle sans lever d'exception. */
    public static boolean isValid(String password) {
        return password != null && password.matches(PASSWORD_REGEX);
    }

    /**
     * Vérifie la règle et lève une 400 avec le message standardisé sinon.
     * À appeler partout où un mot de passe est défini ou modifié.
     */
    public static void validate(String password) {
        if (!isValid(password)) {
            throw new BadRequestException(PASSWORD_MESSAGE);
        }
    }

    /**
     * Vérifie que la confirmation est identique PUIS que le mot de passe respecte la règle.
     */
    public static void validateWithConfirmation(String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            throw new BadRequestException("La confirmation du mot de passe ne correspond pas");
        }
        validate(password);
    }
}