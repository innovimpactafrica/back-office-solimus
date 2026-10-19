package com.example.solimus.dtos.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// [Copropriétaire] Acompte de travaux payé par Wave / Orange Money (≤ TTC du devis)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyDepositDTO {

    @NotNull(message = "Le montant de l'acompte est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(example = "10000")
    private BigDecimal montant;
}
