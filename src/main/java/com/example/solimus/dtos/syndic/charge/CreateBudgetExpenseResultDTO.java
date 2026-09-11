package com.example.solimus.dtos.syndic.charge;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// DTO de confirmation après enregistrement d'une dépense
@Data
@Builder
public class CreateBudgetExpenseResultDTO {
    private boolean success;
    private String message;
    private BigDecimal amountPaid;
    private BigDecimal newWalletBalance;
}
