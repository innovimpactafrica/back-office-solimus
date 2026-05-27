package com.example.solimus.services.syndic;

import com.example.solimus.dtos.intervention.CreateInterventionRequestDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.NearbyProviderDTO;
import com.example.solimus.dtos.property.CreatePropertyDTO;
import com.example.solimus.dtos.property.PropertyDTO;
import com.example.solimus.dtos.residence.CreateResidenceDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.dtos.syndic.CreateCoOwnerDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;

import java.util.List;

public interface SyndicService {
    ResidenceDTO createResidence(CreateResidenceDTO dto);
    List<ResidenceDTO> getMyResidences();
    PropertyDTO createProperty(CreatePropertyDTO dto);
    PropertyDTO addOwner(Long propertyId, Long userId);
    List<NearbyProviderDTO> findNearbyProviders(Long residenceId, Long specialtyId);
    InterventionRequestDTO createInterventionRequest(CreateInterventionRequestDTO dto);
    List<InterventionRequestDTO> getMyInterventionRequests();
    void acceptQuote(Long requestId, Long quoteId);
    List<com.example.solimus.dtos.intervention.SyndicQuoteDTO> getQuotesByInterventionRequest(Long requestId);
    void addCoOwner(CreateCoOwnerDTO dto);

    // ================================================
    // ACOMPTE — versé avant ou au début des travaux
    // ================================================
    PaymentResponseDTO payerAcompte(Long requestId, PayerAcompteDTO dto);

    // ================================================
    // VALIDATION + PAIEMENT SOLDE — après les travaux
    // ================================================
   PaymentResponseDTO validerEtPayerSolde(Long requestId, ValiderTravauxDTO dto);
}
