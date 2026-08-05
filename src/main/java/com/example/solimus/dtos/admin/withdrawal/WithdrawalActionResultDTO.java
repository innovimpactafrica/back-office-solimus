package com.example.solimus.dtos.admin.withdrawal;

import com.example.solimus.enums.WithdrawalStatus;
import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Réponse après validation (Bloc 4) ou refus (Bloc 5) d'une demande de retrait =====
// receiptUrl n'est renseigné qu'après une validation, rejectionReason qu'après un refus.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalActionResultDTO {

    private Long id;
    private WithdrawalStatus status;
    private LocalDateTime processedAt;
    private String receiptUrl;
    private String rejectionReason;
}