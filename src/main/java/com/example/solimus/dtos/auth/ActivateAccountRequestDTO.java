package com.example.solimus.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO utilisé par l'utilisateur pour définir son mot de passe après avoir cliqué
 * sur le lien d'activation envoyé par l'admin.
 */
@Data
public class ActivateAccountRequestDTO {

    /** Token UUID reçu par email (via le lien d'activation). */
    @NotBlank(message = "Le token d'activation est obligatoire")
    private String token;

    /** Nouveau mot de passe choisi par le Syndic. */
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    /** Confirmation du mot de passe (doit correspondre à password). */
    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmPassword;
}
