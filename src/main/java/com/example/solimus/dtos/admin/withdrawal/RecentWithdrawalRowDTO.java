package com.example.solimus.dtos.admin.withdrawal;

import com.example.solimus.enums.WithdrawalStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO — Bloc 3, une ligne du tableau "Derniers retraits effectués" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentWithdrawalRowDTO {

    private LocalDateTime date;
    private BigDecimal amount;
    private WithdrawalStatus status;
}