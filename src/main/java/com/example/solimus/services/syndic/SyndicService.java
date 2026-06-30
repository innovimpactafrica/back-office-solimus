package com.example.solimus.services.syndic;

import com.example.solimus.dtos.charge.ChargeResponseDTO;
import com.example.solimus.dtos.charge.CreateChargeDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.SyndicQuoteDTO;
import com.example.solimus.dtos.syndic.travaux.PayDepositDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;

import java.util.List;

public interface SyndicService {


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
    // ACOMPTE — versé avant ou au début des travaux
    // ================================================
    PaymentResponseDTO payerAcompte(Long requestId, PayDepositDTO dto);

    // ================================================
    // VALIDATION + PAIEMENT SOLDE — après les travaux
    // ================================================
   PaymentResponseDTO validateAndPayBalance(Long requestId, ValiderTravauxDTO dto);


}
