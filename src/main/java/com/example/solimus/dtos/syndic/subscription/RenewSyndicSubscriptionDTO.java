package com.example.solimus.dtos.syndic.subscription;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriptionDuration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO — le syndic renouvelle SA formule actuelle (même formule) et lance son paiement TouchPay (self-service)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RenewSyndicSubscriptionDTO {

    // Optionnelle : par défaut, la durée du dernier abonnement payé
    @Schema(description = "MONTHLY ou YEARLY — par défaut la durée de l'abonnement en cours", example = "MONTHLY")
    private SubscriptionDuration duration;

    @NotNull(message = "Le mode de paiement est obligatoire")
    @Schema(example = "WAVE")
    private PaymentMethod method;
}
