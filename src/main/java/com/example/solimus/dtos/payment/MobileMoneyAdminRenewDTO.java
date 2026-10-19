package com.example.solimus.dtos.payment;

import com.example.solimus.enums.SubscriptionDuration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// [Admin] Renouvellement d'un abonné payé par Wave / Orange Money
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyAdminRenewDTO {

    @NotNull(message = "La formule choisie est obligatoire")
    @Schema(example = "3")
    private Long planId;

    @NotNull(message = "La durée est obligatoire")
    @Schema(example = "MONTHLY")
    private SubscriptionDuration duration;

    @NotNull(message = "La date de début est obligatoire")
    @Schema(example = "2026-10-20")
    private LocalDate startDate;

    private boolean notifyClient;
    private boolean sendInvoiceEmail;
}
