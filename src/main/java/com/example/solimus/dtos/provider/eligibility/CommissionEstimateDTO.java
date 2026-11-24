package com.example.solimus.dtos.provider.eligibility;

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
@Schema(description = "Commission Solimus d'un devis : débitée du wallet à la fin des travaux")
public class CommissionEstimateDTO {
    @Schema(description = "Taux de commission de la résidence (%)", example = "5")
    private BigDecimal commissionRate;
    @Schema(description = "Montant TTC du devis utilisé pour le calcul (FCFA)", example = "47200")
    private BigDecimal quoteAmount;
    @Schema(description = "Commission qui sera débitée à la fin des travaux (FCFA)", example = "2360")
    private BigDecimal commission;
    private BigDecimal walletBalance;
    @Schema(description = "true si le wallet couvre la commission : le devis peut être envoyé")
    private boolean sufficient;
    @Schema(description = "Montant à recharger pour pouvoir envoyer ce devis (0 si suffisant)")
    private BigDecimal missingAmount;
    private String message;
}
