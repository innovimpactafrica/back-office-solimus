package com.example.solimus.services.provider.subscription;

import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanDTO;
import com.example.solimus.dtos.provider.subscription.InitiateSubscriptionPaymentDTO;
import com.example.solimus.dtos.provider.subscription.SubscriptionPaymentResponseDTO;

public interface SubscriptionPaymentService {

    SubscriptionPaymentResponseDTO initiatePayment(InitiateSubscriptionPaymentDTO dto);

    ProviderPlanDTO getProviderPlan();
}
