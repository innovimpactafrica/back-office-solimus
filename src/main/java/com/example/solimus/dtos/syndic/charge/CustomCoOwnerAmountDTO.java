package com.example.solimus.dtos.syndic.charge;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO d'un montant personnalisé pour un copropriétaire, utilisé en mode de répartition CUSTOM
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomCoOwnerAmountDTO {
    @NotNull
    private Long coOwnerId;
    @NotNull private BigDecimal amount;
}