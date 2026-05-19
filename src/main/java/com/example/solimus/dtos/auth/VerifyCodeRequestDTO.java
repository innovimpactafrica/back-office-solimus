package com.example.solimus.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la vérification du code d'activation reçu par email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCodeRequestDTO {

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    @NotBlank(message = "Le code est obligatoire")
    private String code;
}
