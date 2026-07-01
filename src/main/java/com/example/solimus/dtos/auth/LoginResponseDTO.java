package com.example.solimus.dtos.auth;

import com.example.solimus.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * DTO DE RÉPONSE DE CONNEXION
 * ============================================================================
 *
 * Retourne les informations nécessaires après une authentification réussie.
 *
 * Selon le type d'utilisateur et le workflow de sécurité,
 * la réponse peut contenir :
 * - un token JWT ;
 * - les informations de l'utilisateur connecté ;
 * - une indication qu'un OTP est encore requis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDTO {

    // =========================================================================
    // AUTHENTIFICATION
    // =========================================================================

    /**
     * Token JWT utilisé pour accéder aux endpoints sécurisés.
     */
    private String accessToken;

    /**
     * Token de rafraîchissement pour obtenir un nouvel access token.
     */
    private String refreshToken;


    // =========================================================================
    // GESTION DE L'AUTHENTIFICATION À DOUBLE FACTEUR (OTP)
    // =========================================================================

    /**
     * Indique si une validation OTP est encore nécessaire
     * avant d'accorder l'accès complet à l'application.
     *
     * Exemple :
     * - true  -> l'utilisateur doit encore saisir un code OTP.
     * - false -> l'utilisateur est totalement authentifié.
     */
    private boolean otpRequired;


    // =========================================================================
    // CONSTRUCTEUR SIMPLIFIÉ POUR LE FLUX OTP
    // =========================================================================

    /**
     * Utilisé lorsqu'un utilisateur a passé la première étape
     * d'authentification mais doit encore valider son OTP.
     */
    public LoginResponseDTO(boolean otpRequired) {
        this.otpRequired = otpRequired;
    }
}