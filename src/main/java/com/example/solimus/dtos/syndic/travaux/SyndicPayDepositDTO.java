package com.example.solimus.dtos.syndic.travaux;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO d'entrée pour payer un acompte (ou un solde) d'une intervention
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicPayDepositDTO {

    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    private BigDecimal montant;

    // Poste budgétaire choisi. Obligatoire seulement pour le tout premier paiement
    // (acompte ou paiement unique). Pour les paiements suivants, ce champ est ignoré :
    // le poste déjà choisi est repris automatiquement.
    private Long budgetItemId;
}
