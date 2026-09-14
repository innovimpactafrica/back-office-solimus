package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne du tableau des postes budgétaires (onglet "Postes budgétaires")
@Data
public class BudgetItemOverviewDTO {

    private Long id; // Id du poste (BudgetItem.id) — à renvoyer pour choisir ce poste dans le formulaire "Enregistrer une dépense"

    private String libelle; // Nom du poste, ex: "Ascenseurs"

    private BigDecimal montantPrevu; // Montant prévu pour ce poste (BudgetItem.montant)

    // Montant réellement dépensé : somme des transactions du wallet imputées à ce poste précis
    private BigDecimal montantReel;

    // Écart = montantPrevu - montantReel
    private BigDecimal ecart;

    // Pourcentage du poste par rapport au budget total = montantPrevu / budgetTotal * 100
    private Integer percentage;
}