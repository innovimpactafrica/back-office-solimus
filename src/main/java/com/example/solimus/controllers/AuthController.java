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
@Tag(
        name = "Authentification",
        description = "Inscription, connexion, activation de compte et réinitialisation du mot de passe. Tous ces endpoints sont accessibles sans authentification."
)
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Étape 1 — Créer un compte",
            description = "Crée le compte avec les informations de base. Un code OTP à 4 chiffres est envoyé par email pour valider l'adresse."
    )
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody @Valid RegisterRequestDTO dto) {
        authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Compte créé avec succès. Veuillez vérifier votre email.");
    }

    @Operation(
            summary = "Étape 2 — Valider le code OTP reçu par email",
            description = "Vérifie le code à 4 chiffres envoyé par email. Le code expire après 10 minutes."
    )
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@RequestBody @Valid VerifyCodeRequestDTO dto) {
        authService.verifyCode(dto);
        return ResponseEntity.ok("Code validé avec succès.");
    }

    @Operation(
            summary = "Étape 3 — Créer son mot de passe",
            description = "Définit le mot de passe final. Le compte est activé immédiatement après cette étape."
    )
    @PostMapping("/set-password")
    public ResponseEntity<String> setPassword(@RequestBody @Valid SetPasswordRequestDTO dto) {
        authService.setPassword(dto);
        return ResponseEntity.ok("Mot de passe créé. Votre compte est maintenant actif.");
    }

    @Operation(
            summary = "Renvoyer le code OTP d'activation",
            description = "Renvoie un nouveau code OTP par email. Attention : un délai de 60 secondes est requis entre deux envois."
    )
    @PostMapping("/resend-activation-code")
    public ResponseEntity<String> resendActivationCode(@RequestBody @Valid ResendActivationRequestDTO dto) {
        authService.resendActivationCode(dto.getEmail());
        return ResponseEntity.ok("Un nouveau code a été envoyé à votre adresse email.");
    }

    @Operation(
            summary = "Se connecter",
            description = "Connexion avec email ou téléphone + mot de passe. Pour les admins : un OTP supplémentaire est envoyé (double authentification). Pour les autres rôles : le token JWT est retourné directement."
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @Operation(
            summary = "Admin uniquement — Valider le code OTP de connexion (2FA)",
            description = "Deuxième étape de connexion pour les administrateurs. Valide le code OTP reçu par email et retourne le token JWT."
    )
    @PostMapping("/login/verify-otp")
    public ResponseEntity<LoginResponseDTO> verifyAdminLoginOtp(@RequestBody @Valid VerifyCodeRequestDTO dto) {
        return ResponseEntity.ok(authService.verifyAdminLoginOtp(dto));
    }

    @Operation(
            summary = "Se déconnecter",
            description = "Invalide le token JWT. Le token ne peut plus être utilisé après cette opération."
    )
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.ok("Déconnexion réussie.");
    }

    @Operation(
            summary = "Étape 1 — Demander un code de réinitialisation",
            description = "Envoie un code OTP par email pour réinitialiser le mot de passe. Si l'email n'existe pas, aucune erreur n'est retournée (sécurité)."
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO dto) {
        authService.forgotPassword(dto);
        return ResponseEntity.ok("Si l'email existe, un code de réinitialisation a été envoyé.");
    }

    @Operation(
            summary = "Étape 2 — Valider le code de réinitialisation",
            description = "Vérifie le code OTP reçu par email. Retourne un token sécurisé à utiliser dans l'étape 3."
    )
    @PostMapping("/verify-reset-code")
    public ResponseEntity<ForgotPasswordVerifyResponseDTO> verifyResetCode(@RequestBody @Valid VerifyForgotPasswordCodeRequestDTO dto) {
        return ResponseEntity.ok(authService.verifyForgotPasswordCode(dto));
    }

    @Operation(
            summary = "Étape 3 — Définir le nouveau mot de passe",
            description = "Finalise la réinitialisation du mot de passe. Utilise le token reçu à l'étape 2."
    )
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO dto) {
        authService.resetPassword(dto);
        return ResponseEntity.ok("Mot de passe réinitialisé avec succès.");
    }

    @Operation(
            summary = "Valider le lien d'activation (comptes créés par l'admin)",
            description = "Vérifie le token UUID reçu par email. Retourne les informations du compte pour pré-remplir le formulaire. Le lien expire après 1 heure."
    )
    @GetMapping("/validate-activation-token")
    public ResponseEntity<AccountActivationInfoDTO> validateActivationToken(@RequestParam String token) {
        return ResponseEntity.ok(authService.validateActivationToken(token));
    }

    @Operation(
            summary = "Activer le compte et créer le mot de passe",
            description = "Définit le mot de passe final du compte créé par l'admin. Le compte est activé immédiatement."
    )
    @PostMapping("/activate-account")
    public ResponseEntity<String> activateAccount(@RequestBody @Valid ActivateAccountRequestDTO dto) {
        return ResponseEntity.ok(authService.activateAccount(dto));
    }

    @Operation(
            summary = "Renvoyer le lien d'activation",
            description = "Invalide l'ancien lien et envoie un nouveau lien par email. Attention : un délai de 60 secondes est requis entre deux envois."
    )
    @PostMapping("/resend-activation-link")
    public ResponseEntity<String> resendActivationLink(@RequestBody @Valid ResendActivationRequestDTO dto) {
        authService.resendUserActivationLink(dto.getEmail());
        return ResponseEntity.ok("Un nouveau lien d'activation a été envoyé.");
    }
}
