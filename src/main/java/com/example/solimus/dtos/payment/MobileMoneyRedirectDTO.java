package com.example.solimus.dtos.payment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// [Mobile] URLs où renvoyer l'application après paiement / annulation (deep link ou URL interceptée
// par la WebView). Facultatives : sans elles, les pages /payment-success | /payment-failed du backend.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyRedirectDTO {

    @JsonProperty("return_url")
    @JsonAlias("returnUrl")
    @Size(max = 500, message = "return_url : 500 caractères maximum")
    @Schema(description = "Redirection après paiement (reçoit ?paymentStatus=success|failed&ref=…)",
            example = "solimus://payment/success")
    private String returnUrl;

    @JsonProperty("cancel_url")
    @JsonAlias("cancelUrl")
    @Size(max = 500, message = "cancel_url : 500 caractères maximum")
    @Schema(description = "Redirection après annulation (reçoit ?paymentStatus=failed&ref=…)",
            example = "solimus://payment/cancel")
    private String cancelUrl;
}
