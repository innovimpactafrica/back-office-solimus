package com.example.solimus.dtos.provider.subscription;

import com.example.solimus.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envoyé par le prestataire pour initier le paiement de son abonnement.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InitiateSubscriptionPaymentDTO{

    @NotNull(message = "La méthode de paiement est obligatoire")
    private PaymentMethod method;

    /**
     * true → facturation annuelle (yearlyPrice)
     * false / null → facturation mensuelle (monthlyPrice)
     */
    private Boolean annual;
}
