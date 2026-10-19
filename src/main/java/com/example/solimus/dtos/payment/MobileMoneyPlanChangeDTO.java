package com.example.solimus.dtos.payment;

import com.example.solimus.enums.SubscriptionDuration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// [Syndic] Changement de formule payé par Wave / Orange Money — le mode de paiement vient de ?operator=
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyPlanChangeDTO {

    @NotNull(message = "La formule choisie est obligatoire")
    @Schema(example = "3")
    private Long syndicPlanId;

    @NotNull(message = "La durée est obligatoire")
    @Schema(example = "MONTHLY")
    private SubscriptionDuration duration;
}
