package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne du tableau des postes budgétaires (onglet "Postes budgétaires")
@Data
public class BudgetItemOverviewDTO {

    private String libelle; // Nom du poste, ex: "Ascenseurs"

    private BigDecimal montantPrevu; // Montant prévu pour ce poste (BudgetItem.montant)

    // Montant réellement dépensé : calculé via les interventions si poste lié à un bien commun,
    // sinon via les demandes de retrait validées liées à ce poste
    private BigDecimal montantReel;

    // Écart = montantPrevu - montantReel
    private BigDecimal ecart;

    // Pourcentage du poste par rapport au budget total = montantPrevu / budgetTotal * 100
    private Integer percentage;
}