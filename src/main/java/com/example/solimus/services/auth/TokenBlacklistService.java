package com.example.solimus.services.auth;

/**
 * Service pour gérer la blacklist des tokens JWT invalidés (déconnexion).
 */
public interface TokenBlacklistService {

    /**
     * Ajoute un token à la blacklist.
     * @param token Le token JWT à invalider.
     */
    void addToBlackList(String token);

    /**
     * Vérifie si un token est blacklisté.
     */
    boolean isBlackListed(String token);

    /**
     * Nettoie les tokens expirés de la blacklist.
     */
    void cleanupExpiredTokens();
}
