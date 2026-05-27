package com.example.solimus.services.subscription;

import com.example.solimus.dtos.subscription.SouscrirePremiumDTO;
import com.example.solimus.dtos.subscription.SubscriptionDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.entities.User;

public interface SubscriptionService {
    void initialiserAbonnement(User provider);
    PaymentResponseDTO passerEnPremium(Long providerId, SouscrirePremiumDTO dto);
    void renouvelerAbonnementsExpires();
    SubscriptionDTO getMonAbonnement(Long providerId);
    void annulerAbonnement(Long providerId);
}
