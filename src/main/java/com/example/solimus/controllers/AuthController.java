package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.*;
import com.example.solimus.services.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "API pour la gestion de l'inscription et de la connexion")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Inscription", description = "Étape 1 : Création du compte utilisateur.")
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody @Valid RegisterRequestDTO dto) {
        authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Utilisateur créé avec succès. Veuillez vérifier votre email.");
    }

    @Operation(summary = "Connexion", description = "Authentification avec email/téléphone et mot de passe. Si Admin, un OTP est envoyé.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @Operation(summary = "Vérification OTP Admin", description = "Étape finale de connexion pour les administrateurs (2FA).")
    @PostMapping("/login/verify-otp")
    public ResponseEntity<LoginResponseDTO> verifyAdminLoginOtp(@RequestBody @Valid VerifyCodeRequestDTO dto) {
        return ResponseEntity.ok(authService.verifyAdminLoginOtp(dto));
    }

    @Operation(summary = "Vérification du code (OTP)", description = "Étape 2 : Validation de l'email.")
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@RequestBody @Valid VerifyCodeRequestDTO dto) {
        authService.verifyCode(dto);
        return ResponseEntity.ok("Code validé avec succès.");
    }

    @Operation(summary = "Création du mot de passe", description = "Étape 3 : Finalisation de l'activation.")
    @PostMapping("/set-password")
    public ResponseEntity<String> setPassword(@RequestBody @Valid SetPasswordRequestDTO dto) {
        authService.setPassword(dto);
        return ResponseEntity.ok("Mot de passe défini avec succès. Votre compte est activé.");
    }

    @Operation(summary = "Déconnexion", description = "Invalide le token JWT actuel.")
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.ok("Déconnexion réussie.");
    }

    @Operation(summary = "Mot de passe oublié", description = "Demande l'envoi d'un code de réinitialisation.")
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO dto) {
        authService.forgotPassword(dto);
        return ResponseEntity.ok("Si l'email existe, un code de réinitialisation a été envoyé.");
    }

    @Operation(summary = "Réinitialiser le mot de passe", description = "Définit un nouveau mot de passe via un token.")
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO dto) {
        authService.resetPassword(dto);
        return ResponseEntity.ok("Mot de passe réinitialisé avec succès.");
    }

    // ============================================================================
    // 🔑 ACTIVATION DE COMPTE (créé par ADMIN)
    // ============================================================================

    @Operation(
        summary = "Valider le lien d'activation",
        description = "Vérifie le token UUID reçu par email et retourne les infos utilisateur."
    )
    @GetMapping("/validate-activation-token")
    public ResponseEntity<AccountActivationInfoDTO> validateActivationToken(@RequestParam String token) {
        return ResponseEntity.ok(authService.validateActivationToken(token));
    }

    @Operation(
        summary = "Activer le compte",
        description = "Définit le mot de passe final et active le compte."
    )
    @PostMapping("/activate-account")
    public ResponseEntity<String> activateAccount(@RequestBody @Valid ActivateAccountRequestDTO dto) {
        return ResponseEntity.ok(authService.activateAccount(dto));
    }

    @Operation(
        summary = "Renvoyer le lien d'activation",
        description = "Invalide l'ancien token et renvoie un nouveau lien par email. Soumis à un cooldown de 60s."
    )
    @PostMapping("/resend-activation-link")
    public ResponseEntity<String> resendActivationLink(@RequestBody @Valid ResendActivationRequestDTO dto) {
        authService.resendUserActivationLink(dto.getEmail());
        return ResponseEntity.ok("Un nouveau lien d'activation a été envoyé à votre adresse email.");
    }
}
