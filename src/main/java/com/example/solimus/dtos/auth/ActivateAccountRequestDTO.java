package com.example.solimus.dtos.auth;

import com.example.solimus.utils.PasswordValidationUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO utilisé par l'utilisateur pour définir son mot de passe après avoir cliqué
 * sur le lien d'activation envoyé par l'admin (ci dessous).
 */
@Data
public class ActivateAccountRequestDTO {

    /** Token UUID reçu par email (via le lien d'activation). */
    @NotBlank(message = "Le token d'activation est obligatoire")
    private String token;

    /** Nouveau mot de passe choisi */
    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = PasswordValidationUtil.MIN_LENGTH,
            message = "Le mot de passe doit contenir au moins 8 caractères")
    @Pattern(regexp = PasswordValidationUtil.PASSWORD_REGEX,
            message = PasswordValidationUtil.PASSWORD_MESSAGE)
    private String password;

    /** Confirmation du mot de passe (doit correspondre à password). */
    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmPassword;
}
