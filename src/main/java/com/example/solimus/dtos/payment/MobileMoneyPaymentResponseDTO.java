package com.example.solimus.dtos.payment;

import com.example.solimus.enums.MobileMoneyOperator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Réponse d'initiation d'un paiement Wave / Orange Money — le front ouvre payment_url
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyPaymentResponseDTO {

    private boolean success;
    private String message;

    // Référence interne Solimus (SYR-, CPY-, ...) — aussi transmise à GU comme idFromClient
    private String reference;

    private MobileMoneyOperator operator;

    @Schema(example = "SNPAIEMENTWAVE")
    private String serviceCode;

    private BigDecimal amount;

    // Frais annoncés par GU (champ "fees"), informatif
    private BigDecimal fees;

    // Identifiant / numéro de transaction côté GU
    private String idFromGU;
    private String numTransaction;

    // Statut renvoyé par GU à l'initiation (ex: INITIATED)
    private String status;

    @JsonProperty("payment_url")
    @Schema(description = "Page de paiement Wave / Orange Money à ouvrir",
            example = "https://pay.wave.com/c/cos-27zhmjcq811w6?a=1500&c=XOF&m=Touch%20point%20%2A%20Into")
    private String paymentUrl;

    // Où l'application sera renvoyée à la fin (URL fournie par le mobile, sinon page par défaut) —
    // reçoit ?paymentStatus=success|failed&ref=… : utile pour intercepter la navigation de la WebView
    @JsonProperty("return_url")
    private String returnUrl;

    @JsonProperty("cancel_url")
    private String cancelUrl;
}
