package com.example.solimus.dtos.owner.charge;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

//DTO d'une ligne de répartition par poste budgétaire
@Data
@Builder
public class ChargeBreakdownLineDTO {
    private String label; // Ex: "Entretien parties communes"
    private BigDecimal amount; // Part de ce copropriétaire sur ce poste
}
