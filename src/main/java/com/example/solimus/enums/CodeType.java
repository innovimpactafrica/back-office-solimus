package com.example.solimus.enums;

/**
 * Type de code d'activation (OTP).
 */
public enum CodeType {
    // Activation standard après inscription utilisateur (code OTP 4/6 chiffres, 15 min)
    ACTIVATION,

    // Réinitialisation du mot de passe (code OTP mobile ou token UUID web, 15 min)
    PASSWORD_RESET,

    // Activation de compte créé par l'admin ou renvoi d'activation
    // Web : token UUID dans lien (15 min)
    // Mobile : code OTP 4 chiffres (15 min)
    ACCOUNT_ACTIVATION
}
