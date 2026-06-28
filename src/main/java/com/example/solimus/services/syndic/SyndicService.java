package com.example.solimus.services.syndic;

import com.example.solimus.dtos.charge.ChargeResponseDTO;
import com.example.solimus.dtos.charge.CreateChargeDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.NearbyProviderDTO;
import com.example.solimus.dtos.intervention.SyndicQuoteDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.dtos.provider.WithdrawalRequestDTO;
import com.example.solimus.enums.WithdrawalStatus;

import java.util.List;

public interface SyndicService {

    List<NearbyProviderDTO> findNearbyProviders(Long residenceId, Long specialtyId);

    List<InterventionRequestDTO> getMyInterventionRequests();
    void acceptQuote(Long requestId, Long quoteId);
    List<SyndicQuoteDTO> getQuotesByInterventionRequest(Long requestId);

    // ================================================
    // INTERVENTIONS — prise en charge par le syndic
    // ================================================
    InterventionRequestDTO assignIntervention(Long requestId);

    // ================================================
    // CHARGES — gestion des charges de copropriété
    // ================================================
    String createCharge(CreateChargeDTO dto);
    List<ChargeResponseDTO> getChargesByResidence(Long residenceId);
    void deleteCharge(Long chargeId);

    // ================================================
    // BIENS — gestion des biens d'une résidence
    // ================================================
    List<PropertyDTO> getPropertiesByResidence(Long residenceId);

    // ================================================
    // ACOMPTE — versé avant ou au début des travaux
    // ================================================
    PaymentResponseDTO payerAcompte(Long requestId, PayerAcompteDTO dto);

    // ================================================
    // VALIDATION + PAIEMENT SOLDE — après les travaux
    // ================================================
   PaymentResponseDTO validerEtPayerSolde(Long requestId, ValiderTravauxDTO dto);

    // ================================================
    // RETRAITS PRESTATAIRES — gestion des demandes de retrait
    // ================================================
   List<WithdrawalRequestDTO> getWithdrawalRequests(WithdrawalStatus status);
   WithdrawalRequestDTO confirmWithdrawal(Long withdrawalId);
   WithdrawalRequestDTO rejectWithdrawal(Long withdrawalId, String motifRefus);
}
