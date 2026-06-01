package com.example.solimus.dtos.charge;

import com.example.solimus.enums.ChargePaymentMethod;
import com.example.solimus.enums.ChargeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargePaymentReceiptDTO {
    private String reference;        // CPY-123456
    private String chargeTitle;      // "Charges mensuelles"
    private BigDecimal amount;       // 150 000
    private ChargePaymentMethod method; // WAVE
    private LocalDateTime paidAt;    // 13/05/2026
    private ChargeStatus status;     // PAYEE
}
