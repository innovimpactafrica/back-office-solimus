package com.example.solimus.dtos.admin.wallet;

import com.example.solimus.enums.CommissionSource;
import com.example.solimus.enums.PlatformWalletTransactionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformWalletTransactionDTO {
    private Long id;
    private PlatformWalletTransactionCategory category;
    private String categoryLabel;
    private CommissionSource source;
    private String sourceLabel;
    /** Commission encaissée par Solimus. */
    private BigDecimal amount;
    /** Montant du paiement de travaux. */
    private BigDecimal grossAmount;
    /** Taux appliqué (%). */
    private BigDecimal commissionRate;
    private String label;
    private String paymentReference;
    private Long interventionId;
    private String interventionTitle;
    private Long providerId;
    private String providerName;
    private Long syndicId;
    private String syndicName;
    private LocalDateTime transactionDate;
}
