package com.example.solimus.dtos.syndic.travaux;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Une ligne du menu déroulant "Poste budgétaire" du modal de paiement travaux
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravauxBudgetItemOptionDTO {
    private Long id;
    private String libelle;
    private BigDecimal montantPrevu;
    private BigDecimal montantReel;
}
