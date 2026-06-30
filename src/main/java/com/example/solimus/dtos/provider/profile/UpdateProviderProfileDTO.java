package com.example.solimus.dtos.provider.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour la récupération et la mise à jour des informations personnelles du prestataire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProviderProfileDTO {

    // Modifiables directement
    private String companyName;
    private String firstName;
    private String lastName;

    // Modifiables avec vérification
    private String phone;
    private String email;

    // Affichage uniquement (non modifiable)
    private String profilePhotoUrl;
    private String specialtyName;
    private String interventionZone;
}
