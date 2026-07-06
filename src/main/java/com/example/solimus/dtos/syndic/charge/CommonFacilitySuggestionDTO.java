package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO suggestion d'équipement commun pour la création d'un post budgetaire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonFacilitySuggestionDTO {

    private Long id;
    private String name;
    private String icon;
}
