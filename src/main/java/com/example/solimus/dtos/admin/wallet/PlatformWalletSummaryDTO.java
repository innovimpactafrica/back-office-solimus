package com.example.solimus.dtos.admin.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wallet de la plateforme Solimus : commissions encaissées sur les travaux")
public class PlatformWalletSummaryDTO {

    @Schema(description = "Solde total (somme de toutes les commissions)", example = "12500")
    private BigDecimal balance;

    @Schema(description = "Commissions encaissées depuis le 1er du mois", example = "3500")
    private BigDecimal totalThisMonth;

    @Schema(description = "Commissions encaissées le mois précédent", example = "9000")
    private BigDecimal totalLastMonth;

    @Schema(description = "Nombre total de mouvements", example = "18")
    private long transactionCount;

    @Schema(example = "FCFA")
    private String currency;
}
