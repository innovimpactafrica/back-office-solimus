package com.example.solimus.dtos.syndic.subscription;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriptionDuration;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO — le syndic choisit une nouvelle formule et lance son propre paiement TouchPay (self-service)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InitiateSyndicPlanChangeDTO {

    @NotNull(message = "La formule choisie est obligatoire")
    private Long syndicPlanId;

    @NotNull(message = "La durée est obligatoire")
    private SubscriptionDuration duration;

    // Choisi par le syndic dans son propre formulaire avant la redirection TouchPay
    @NotNull(message = "Le mode de paiement est obligatoire")
    private PaymentMethod method;
}
