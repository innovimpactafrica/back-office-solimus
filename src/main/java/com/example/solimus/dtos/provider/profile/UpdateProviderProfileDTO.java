package com.example.solimus.dtos.provider.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProviderProfileDTO {

    private String companyName;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String specialtyName;      // affiché mais non modifiable (le backend ignorera sa mise à jour)
    private String interventionZone;   // Ex: "Dakar" (le texte de l'autocompléte)
    private java.math.BigDecimal latitude;   // Coordonnée GPS
    private java.math.BigDecimal longitude;  // Coordonnée GPS
    private String profilePhotoUrl;    // A retourner pour affichage
}
