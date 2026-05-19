package com.example.solimus.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la requête de connexion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "L'email ou le téléphone est obligatoire")
    private String identifier;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String password;
}
