package com.example.solimus.dtos.admin;

import com.example.solimus.enums.ERole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO utilisé par l'Admin pour créer un compte utilisateur (Syndic, etc.).
 * Le mot de passe n'est PAS saisi ici — l'utilisateur le définit lui-même via le lien d'activation.
 */
@Data
public class CreateUserRequestDTO {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(
        regexp = "^[+]?[0-9\\s\\-\\(\\)]{8,25}$",
        message = "Le numéro de téléphone doit être valide"
    )
    private String phone;

    @NotNull(message = "Le rôle est obligatoire")
    private ERole role;
}
