package com.example.solimus.services.auth;

import com.example.solimus.dtos.auth.*;

/**
 * Interface définissant les services d'authentification et de gestion de compte.
 */
public interface AuthService {
    
    /**
     * Inscription d'un nouvel utilisateur (Prestataire ou Copropriétaire).
     */
    void register(RegisterRequestDTO dto);

    /**
     * Connexion d'un utilisateur et génération du token JWT.
     */
    LoginResponseDTO login(LoginRequestDTO dto);

    /**
     * Vérification du code OTP reçu par email lors de l'activation du compte.
     */
    void verifyCode(VerifyCodeRequestDTO dto);

    /**
     * Vérification du code OTP lors de la connexion administrateur (2FA).
     */
    LoginResponseDTO verifyAdminLoginOtp(VerifyCodeRequestDTO dto);

    /**
     * Définition du mot de passe final après validation du code OTP.
     */
    void setPassword(SetPasswordRequestDTO dto);

    /**
     * Déconnexion (invalidation du token JWT).
     */
    void logout(String token);

    /**
     * Demande de réinitialisation de mot de passe (envoi d'un token par email).
     */
    void forgotPassword(ForgotPasswordRequestDTO dto);

    /**
     * Réinitialisation effective du mot de passe avec le token reçu.
     */
    void resetPassword(ResetPasswordRequestDTO dto);

    /**
     * Vérification du code OTP de réinitialisation mobile.
     */
    ForgotPasswordVerifyResponseDTO verifyForgotPasswordCode(VerifyForgotPasswordCodeRequestDTO dto);


    // ============================================================================
    // 🔑 ACTIVATION DE COMPTE SYNDIC (créé par ADMIN)
    // ============================================================================

    /**
     * Valide le token d'activation du compte utilisateur reçu par email.
     * Si valide, retourne les informations du compte pour pré-remplir le formulaire.
     *
     * @param token Le token UUID reçu dans le lien d'email.
     * @return Les informations de l'utilisateur (prénom, nom, email, téléphone).
     */
    AccountActivationInfoDTO validateActivationToken(String token);

    /**
     * Renvoie un nouveau lien d'activation à l'utilisateur.
     * Inclut un cooldown pour limiter la fréquence des envois.
     * @param email L'email de l'utilisateur concerné.
     */
    void resendUserActivationLink(String email);

    /**
     * Renvoie un nouveau code OTP d'activation à l'utilisateur mobile.
     * Inclut un cooldown pour limiter la fréquence des envois.
     * @param email L'email de l'utilisateur concerné.
     */
    void resendActivationCode(String email);

    /**
     * Finalise l'activation du compte.
     * @param dto Contient le token et le mot de passe.
     * @return Un message de confirmation de succès.
     */
    String activateAccount(
            com.example.solimus.dtos.auth.ActivateAccountRequestDTO dto);
}
