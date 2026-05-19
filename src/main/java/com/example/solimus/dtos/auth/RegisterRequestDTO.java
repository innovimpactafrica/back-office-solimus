package com.example.solimus.dtos.auth;

import com.example.solimus.enums.ERole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour l'étape initiale de l'inscription.
 * L'utilisateur ne saisit PAS de mot de passe à ce stade.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^[+]?[0-9\\s\\-\\(\\)]{8,25}$", message = "Le numéro de téléphone doit être valide")
    private String phone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    @NotNull(message = "Le rôle est obligatoire")
    private ERole role;

    // Champs spécifiques au prestataire (obligatoires si role == ROLE_PRESTATAIRE)
    private String companyName;
    private Long specialtyId;


    // Photo de profil optionnelle dès l'inscription
    private String profilePhotoUrl;

    // Coordonnées GPS (obligatoires pour les prestataires)
    private BigDecimal latitude;
    private BigDecimal longitude;
}
