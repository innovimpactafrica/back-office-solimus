package com.example.solimus.dtos.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
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

    // URLs de retour de l'application mobile (facultatives) — voir MobileMoneyRedirectDTO
    @JsonProperty("return_url")
    @JsonAlias("returnUrl")
    @Size(max = 500, message = "return_url : 500 caractères maximum")
    @Schema(description = "Redirection après paiement (reçoit ?paymentStatus=success|failed&ref=…)", example = "solimus://payment/success")
    private String returnUrl;

    @JsonProperty("cancel_url")
    @JsonAlias("cancelUrl")
    @Size(max = 500, message = "cancel_url : 500 caractères maximum")
    @Schema(description = "Redirection après annulation (reçoit ?paymentStatus=failed&ref=…)", example = "solimus://payment/cancel")
    private String cancelUrl;
}
