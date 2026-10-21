package com.example.solimus.dtos.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// [Copropriétaire] Acompte de travaux payé par Wave / Orange Money (≤ TTC du devis)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyDepositDTO {

    @NotNull(message = "Le montant de l'acompte est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(example = "10000")
    private BigDecimal montant;

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
