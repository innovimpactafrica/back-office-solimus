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
}
