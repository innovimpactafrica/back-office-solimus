package com.example.solimus.dtos.admin.settings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Corps de la requête PUT /api/admin/withdrawal-settings =====
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWithdrawalSettingsDTO {

    @NotNull(message = "La limite mensuelle est obligatoire")
    @Positive(message = "La limite mensuelle doit être positive")
    private BigDecimal monthlyLimit;
}