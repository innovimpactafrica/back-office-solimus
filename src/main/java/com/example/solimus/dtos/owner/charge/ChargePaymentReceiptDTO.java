package com.example.solimus.dtos.owner.charge;

//DTO Reçu paiement
import com.example.solimus.enums.ChargePaymentMethod;
import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.enums.PaymentStatus;
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
    private String reference;
    private String chargeTitle;
    private BigDecimal amount;
    private ChargePaymentMethod method;
    private LocalDateTime paidAt;
    private PaymentStatus status;
}
