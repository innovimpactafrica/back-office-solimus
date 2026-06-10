package com.example.solimus.dtos.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour sélectionner un copropriétaire lors de l'affectation d'un lot
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerSelectionDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String profilePhotoUrl;
    private long ownedPropertiesCount;//nombre de lots possédés
}
