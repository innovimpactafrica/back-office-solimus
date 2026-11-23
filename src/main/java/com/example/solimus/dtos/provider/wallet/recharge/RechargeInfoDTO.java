package com.example.solimus.dtos.provider.wallet.recharge;

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
@Schema(description = "Écran « Recharger mon compte » : solde, seuil d'accès aux demandes et instructions de paiement")
public class RechargeInfoDTO {
    private BigDecimal walletBalance;
    @Schema(description = "Le solde doit être STRICTEMENT supérieur à ce montant pour voir les demandes")
    private BigDecimal minimumBalanceExclusive;
    private boolean eligible;
    @Schema(description = "Montant minimum à recharger pour accéder aux demandes (0 si déjà éligible)")
    private BigDecimal missingAmount;
    private BigDecimal minRechargeAmount;
    private BigDecimal maxRechargeAmount;
    @Schema(description = "Où et comment payer Solimus (numéro Wave / Orange Money, compte bancaire…)")
    private String instructions;
    @Schema(description = "Recharges en attente de validation par Solimus")
    private long pendingCount;
    private int maxPendingRecharges;
}
