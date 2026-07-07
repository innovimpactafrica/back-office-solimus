package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'un poste budgétaire affiché en aperçu sur une carte
@Data
public class BudgetItemPreviewDTO {

    private String libelle;

    private BigDecimal montant;

    private Integer percentage; // montant / budgetTotal * 100, sert à dessiner la barre
}