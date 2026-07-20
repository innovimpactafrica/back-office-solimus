package com.example.solimus.dtos.syndic.wallet;

import com.example.solimus.enums.WithdrawalMode;
import com.example.solimus.enums.WithdrawalStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// ===== DTO DETAIL COMPLET D'UN RETRAIT =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalDetailDTO {

    private Long id;
    private BigDecimal amount;
    private LocalDateTime requestedAt;

    private WithdrawalMode mode;
    private String modeLabel;      // "Virement Bancaire", "Wave", "Orange Money"

    private String accountNumber;
    private String residenceName;
    private String budgetItemLabel; // libelle du poste budgetaire, peut etre null

    private WithdrawalStatus status;
    private String statusLabel;

    private List<WithdrawalProgressStepDTO> progressSteps;
}
