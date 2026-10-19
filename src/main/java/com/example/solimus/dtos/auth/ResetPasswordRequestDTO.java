package com.example.solimus.dtos.auth;

import com.example.solimus.utils.PasswordValidationUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour finaliser la réinitialisation du mot de passe avec un token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequestDTO {

    //token généré dans verifyForgotPasswordCode
    @NotBlank(message = "Le token est obligatoire")
    private String token;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = PasswordValidationUtil.MIN_LENGTH,
            message = "Le mot de passe doit contenir au moins 8 caractères")
    @Pattern(regexp = PasswordValidationUtil.PASSWORD_REGEX,
            message = PasswordValidationUtil.PASSWORD_MESSAGE)
    private String newPassword;

    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmPassword;
}
