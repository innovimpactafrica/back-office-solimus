package com.example.solimus.services.auth;

import java.util.Date;

/**
 * Contrat de service pour les opérations sur les tokens JWT.
 * Définit les méthodes essentielles pour la gestion du cycle de vie des tokens.
 */
public interface JwtService {

    /**
     * Génère un nouveau token JWT avec les informations utilisateur.
     */
    String generateToken(String email, String role, Long id);

    /**
     * Génère un token de rafraîchissement avec une durée de vie plus longue.
     */
    String generateRefreshToken(String email);

    /**
     * Extrait l'email (subject) du token.
     */
    String extractEmail(String token);

    /**
     * Extrait le rôle utilisateur depuis les claims du token.
     */
    String extractRole(String token);


    /**
     * Extrait le nom d'utilisateur (alias pour l'email).
     */
    String extractUsername(String token);

    /**
     * Vérifie si le token a expiré.
     */
    Boolean isTokenExpired(String token);


    /**
     * Vérifie la validité générale du token.
     */
    Boolean isTokenValid(String token);

    /**
     * Extrait la date d'expiration d'un token.
     */
    Date extractExpiration(String token);
}
