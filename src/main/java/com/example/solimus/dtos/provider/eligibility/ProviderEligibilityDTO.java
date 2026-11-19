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
@Schema(description = "Accès du prestataire aux demandes de travaux (solde de wallet minimum)")
public class ProviderEligibilityDTO {

    @Schema(description = "true : le prestataire voit les demandes de travaux et peut envoyer des devis")
    private boolean eligible;

    @Schema(example = "2500")
    private BigDecimal walletBalance;

    @Schema(description = "Le solde doit être STRICTEMENT supérieur à ce montant", example = "1000")
    private BigDecimal minimumBalanceExclusive;

    @Schema(description = "Montant à ajouter pour accéder aux demandes (0 si déjà éligible)", example = "0")
    private BigDecimal missingAmount;

    private String message;
}
