package com.example.solimus.dtos.auth;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour demander la réinitialisation du mot de passe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequestDTO {

    @NotBlank(message = "L'identifiant (e-mail ou téléphone) est obligatoire")
    private String emailOrPhone;
}

