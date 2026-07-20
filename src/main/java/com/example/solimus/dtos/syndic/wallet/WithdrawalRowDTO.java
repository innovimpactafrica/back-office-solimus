package com.example.solimus.dtos.syndic.wallet;

import com.example.solimus.enums.WithdrawalMode;
import com.example.solimus.enums.WithdrawalStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO LIGNE - HISTORIQUE DES RETRAITS =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRowDTO {

    private Long id;
    private LocalDateTime date;
    private BigDecimal amount;

    private WithdrawalMode mode;
    private String modeLabel;

    private WithdrawalStatus status;
    private String statusLabel;
}