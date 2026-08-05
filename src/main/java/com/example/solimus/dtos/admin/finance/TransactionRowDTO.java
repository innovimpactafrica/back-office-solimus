package com.example.solimus.dtos.admin.finance;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriberType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO — Bloc 5 : une ligne du tableau "Transactions Récentes" (Admin > Finances) =====
// status renvoie l'énumération réelle PaymentStatus du projet (PENDING/COMPLETED/FAILED), pas les
// libellés "PAYE/EN_ATTENTE/ECHOUE" — cohérent avec le reste de l'API admin.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRowDTO {

    private String reference;
    private String clientName;
    private SubscriberType type;
    private String planName;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private LocalDateTime createdAt;
}
