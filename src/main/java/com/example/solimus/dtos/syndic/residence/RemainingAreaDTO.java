package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO pour GET /residences/{id}/remaining-area — consommé par le formulaire d'ajout d'appartement,
// pour afficher/mettre à jour en temps réel la superficie encore disponible
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RemainingAreaDTO {
    private BigDecimal totalArea;
    private BigDecimal occupiedArea;
    private BigDecimal remainingArea;
}
