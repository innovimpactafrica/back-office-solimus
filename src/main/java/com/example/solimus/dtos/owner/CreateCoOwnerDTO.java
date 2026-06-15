package com.example.solimus.dtos.owner;

import com.example.solimus.enums.Nationality;
import com.example.solimus.enums.Title;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;


//DTO de création d'un copropriétaire
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCoOwnerDTO {

    // -------------------------------------------------------------------------
    // INFORMATIONS OBLIGATOIRES — déjà dans User
    // -------------------------------------------------------------------------

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^[+]?[0-9\\s\\-\\(\\)]{8,25}$",
             message = "Format de téléphone invalide")
    private String phone;


    // -------------------------------------------------------------------------
    // INFORMATIONS OPTIONNELLES — stockées dans CoOwnerProfile
    // -------------------------------------------------------------------------

    /** Civilité : MR, MRS, COMPANY */
    private Title title;

    /** Date de naissance */
    private LocalDate birthDate;

    /** Nationalité */
    private Nationality nationality;

    /** Téléphone secondaire */
    private String secondaryPhone;

    /** Adresse personnelle */
    private String address;

    /** Photo de profil — URL Minio après upload */
    private String photoUrl;


    // -------------------------------------------------------------------------
    // BIENS À AFFECTER — optionnel à la création
    // -------------------------------------------------------------------------

    /**
     * Liste des biens à affecter au copropriétaire.
     * Chaque entrée contient une résidence + les lots sélectionnés.
     */
    private List<CoOwnerPropertyAssignmentDTO> properties;
}
