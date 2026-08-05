package com.example.solimus.dtos.admin.withdrawal;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO — Bloc 3, "Informations générales" de la fiche détail d'une demande de retrait =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalGeneralInfoDTO {

    private BigDecimal amount;
    private LocalDateTime requestedAt;
    private String mode;
    private String beneficiaryAccount;
    // Motif du retrait — uniquement pour un syndic, null pour un prestataire (pas de champ équivalent)
    private String reason;
}