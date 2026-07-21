package com.example.solimus.dtos.owner.charge;

import com.example.solimus.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO REPONSE - STATUT D'UN PAIEMENT DE CHARGE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargePaymentStatusDTO {

    private String reference;
    private PaymentStatus status;
    private BigDecimal amount;
    private LocalDateTime paidAt;
}
