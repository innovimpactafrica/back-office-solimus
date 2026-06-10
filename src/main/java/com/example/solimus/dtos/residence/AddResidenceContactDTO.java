package com.example.solimus.dtos.residence;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =============================================================================
//
//  ADD RESIDENCE CONTACT DTO
//  Permet d'ajouter un contact clé à une résidence.
// =============================================================================
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddResidenceContactDTO {

    // -------------------------------------------------------------------------
    // INFORMATIONS OBLIGATOIRES
    // -------------------------------------------------------------------------

    /**
     * Nom complet du contact.
     */
    @NotBlank(message = "Le nom est obligatoire")
    private String fullName;

    /**
     * Rôle du contact dans la résidence.
     * Exemple : "Président du Conseil", "Gardien Principal", "Référent technique"
     */
    @NotBlank(message = "Le rôle est obligatoire")
    private String role;


    // -------------------------------------------------------------------------
    // INFORMATIONS DE CONTACT (optionnel)
    // -------------------------------------------------------------------------

    /**
     * Adresse email du contact.
     */
    private String email;

    /**
     * Numéro de téléphone du contact.
     */
    private String phone;

    /**
     * Photo du contact — URL Minio après upload.
     */
    private String photoUrl;
}
