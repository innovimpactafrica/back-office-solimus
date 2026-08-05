package com.example.solimus.dtos.admin.withdrawal;

import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.WithdrawalStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO — Bloc 2 : une ligne de la liste "Admin > Demandes de retraits" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestRowDTO {

    private Long id;
    private SubscriberType type;//Pour différencier les demandes de retrait de syndic et prestataire

    private String companyName;
    private LocalDateTime requestedAt;
    private BigDecimal amount;
    private String mode;
    private WithdrawalStatus status;
}