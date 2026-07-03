package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.WalletTransactionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionDTO {

    private Long id;

    private String label;

    private String reference;

    private LocalDateTime transactionDate;

    private BigDecimal amount; // signé : négatif = dépense, positif = entrée

    private String mode;

    private WalletTransactionCategory category;
}
