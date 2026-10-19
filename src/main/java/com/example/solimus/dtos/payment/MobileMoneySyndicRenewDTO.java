package com.example.solimus.dtos.payment;

import com.example.solimus.enums.SubscriptionDuration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// [Syndic] Renouvellement de la formule actuelle payé par Wave / Orange Money
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneySyndicRenewDTO {

    @Schema(description = "MONTHLY ou YEARLY — par défaut la durée de l'abonnement en cours", example = "MONTHLY")
    private SubscriptionDuration duration;
}
