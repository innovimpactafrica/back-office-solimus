package com.example.solimus.dtos.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargePaymentResponseDTO {
    private boolean success;
    private String message;
    private String transactionReference; // CPY-123456
    private BigDecimal amount;
    private String paymentUrl;           // URL WebView TouchPay
}
