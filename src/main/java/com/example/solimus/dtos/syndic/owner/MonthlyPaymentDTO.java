package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO pour un paiement mensuel (historique)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyPaymentDTO {

    private Integer month;
    private BigDecimal amount;
}
