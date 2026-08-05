package com.example.solimus.dtos.admin.settings;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Réponse GET /api/admin/withdrawal-settings =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalSettingsDTO {

    private BigDecimal monthlyLimit;
}