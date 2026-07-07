package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne du tableau des postes budgétaires (onglet "Postes budgétaires")
@Data
public class BudgetItemOverviewDTO {

    private String libelle; // Nom du poste, ex: "Ascenseurs"

    private BigDecimal montantPrevu; // Montant prévu pour ce poste (BudgetItem.montant)

    // VALEUR PROVISOIRE V1 : montantReel = montantPrevu (pas de vrai calcul par poste).
    private BigDecimal montantReel;

    // VALEUR PROVISOIRE V1 : toujours 0 tant que montantReel = montantPrevu.
    private BigDecimal ecart;

    // Pourcentage du poste par rapport au budget total = montantPrevu / budgetTotal * 100
    private Integer percentage;
}