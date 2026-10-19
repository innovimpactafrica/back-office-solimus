package com.example.solimus.dtos.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// [Prestataire] Souscription / renouvellement payé par Wave / Orange Money
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyProviderSubscriptionDTO {

    @NotNull(message = "La formule d'abonnement est obligatoire")
    @Schema(example = "1")
    private Long providerPlanId;

    @Schema(description = "true = tarif annuel, false/absent = mensuel", example = "false")
    private Boolean annual;
}
