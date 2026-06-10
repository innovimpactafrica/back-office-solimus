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
 * ============================================================================
 * DTO D'INSCRIPTION
 * ============================================================================
 *
 * Utilisé lors de l'inscription d'un nouvel utilisateur.
 *
 * Ce DTO est commun aux :
 * - Prestataires
 * - Copropriétaires
 *
 * Les Syndics ne peuvent pas s'inscrire eux-mêmes.
 * Ils sont créés par l'administrateur.
 *
 * À cette étape, aucun mot de passe n'est demandé.
 * L'utilisateur recevra un code OTP pour activer son compte.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {

    // =========================================================================
    // INFORMATIONS PERSONNELLES
    // =========================================================================

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(
            regexp = "^[+]?[0-9\\s\\-\\(\\)]{8,25}$",
            message = "Le numéro de téléphone doit être valide"
    )
    private String phone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;


    // =========================================================================
    // RÔLE DEMANDÉ
    // =========================================================================

    /**
     * Rôle choisi lors de l'inscription :
     * - ROLE_PRESTATAIRE
     * - ROLE_COPROPRIETAIRE
     */
    @NotNull(message = "Le rôle est obligatoire")
    private ERole role;


    // =========================================================================
    // INFORMATIONS SPÉCIFIQUES AU PRESTATAIRE
    // =========================================================================

    /**
     * Nom de l'entreprise du prestataire.
     */
    private String companyName;

    /**
     * ID de la spécialité :
     * Plomberie, Électricité, Climatisation, etc.
     */
    private Long specialtyId;

    /**
     * Nom de la zone d'intervention
     */
    private String interventionZone;

    /**
     * Coordonnées GPS utilisées pour calculer
     * la proximité avec les résidences.
     */
    private BigDecimal latitude;

    private BigDecimal longitude;


    // =========================================================================
    // INFORMATIONS SPÉCIFIQUES AU COPROPRIÉTAIRE
    // =========================================================================

    /**
     * Résidence dans laquelle se trouve
     * le copropriétaire.
     */
    private Long residenceId;

    /**
     * Bien (appartement, studio, local, etc.)
     * appartenant au copropriétaire.
     */
    private Long propertyId;
}