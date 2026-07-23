package com.example.solimus.services.provider.subscription;

import com.example.solimus.dtos.admin.subscription.ProviderPlanDTO;
import com.example.solimus.dtos.provider.subscription.InitiateSubscriptionPaymentDTO;
import com.example.solimus.dtos.provider.subscription.SubscriptionPaymentResponseDTO;

import java.util.List;

public interface SubscriptionPaymentService {

    SubscriptionPaymentResponseDTO initiatePayment(InitiateSubscriptionPaymentDTO dto);

    // Liste des formules prestataire actuellement actives, proposées au choix
    List<ProviderPlanDTO> getProviderPlans();
}
