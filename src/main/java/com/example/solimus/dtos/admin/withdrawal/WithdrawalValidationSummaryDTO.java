package com.example.solimus.dtos.admin.withdrawal;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 4, Partie 1 : récapitulatif affiché dans la modale de validation d'un retrait =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalValidationSummaryDTO {

    // Raison sociale si renseignée, sinon prénom + nom du responsable
    private String displayName;
    private BigDecimal amount;
    private String mode;
}