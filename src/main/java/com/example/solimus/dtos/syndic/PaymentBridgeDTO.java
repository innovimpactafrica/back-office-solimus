package com.example.solimus.dtos.syndic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO servant de pont (bridge) pour transmettre les informations de paiement nécessaires
 * au script TouchPay chargé par la WebView.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentBridgeDTO {
    private String merchantToken;
    private String transactionReference;
    private String agencyCode;
    private String serviceId;
    private String hostedScriptUrl;
    private BigDecimal amount;
    private String city;
    private String successRedirectUrl;
    private String failedRedirectUrl;
    private String customerEmail;
    private String customerFirstName;
    private String customerLastName;
    private String customerPhone;

}
