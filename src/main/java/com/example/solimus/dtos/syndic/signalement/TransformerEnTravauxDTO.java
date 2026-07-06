package com.example.solimus.dtos.syndic.signalement;

import com.example.solimus.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformerEnTravauxDTO {

    private Long specialtyId;       // "Catégorie de l'incident"
    private UrgencyLevel priorite;  // réutilise l'enum existant à 3 valeurs
}
