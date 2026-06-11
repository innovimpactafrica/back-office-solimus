package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.admin.SpecialtyDTO;
import com.example.solimus.dtos.intervention.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.intervention.NearbyProviderDTO;
import com.example.solimus.dtos.intervention.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.intervention.OwnerInterventionSummaryDTO;
import com.example.solimus.dtos.intervention.SyndicQuoteDTO;
import com.example.solimus.dtos.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;

import java.util.List;

public interface OwnerInterventionService {

    /**
     * Lister toutes les spécialités disponibles.
     */
    List<SpecialtyDTO> getAllSpecialties();

    /**
     * Lister les parties communes d'une résidence.
     */
    List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId);

    /**
     * Trouve les prestataires proches de la résidence du copropriétaire connecté.
     */
    List<NearbyProviderDTO> findNearbyProviders(Long specialtyId);

    /**
     * Crée une nouvelle demande d'intervention initiée par le copropriétaire.
     */
    OwnerInterventionDetailDTO createIntervention(CreateOwnerInterventionRequestDTO dto, List<String> photoUrls);

    /**
     * Liste toutes les interventions du copropriétaire connecté.
     */
    List<OwnerInterventionSummaryDTO> getMyInterventions();

    /**
     * Récupère le détail d'une intervention spécifique du copropriétaire.
     */
    OwnerInterventionDetailDTO getInterventionDetail(Long interventionId);

    /**
     * Lister les devis reçus pour une intervention.
     */
    List<SyndicQuoteDTO> getQuotesByIntervention(Long interventionId);

    /**
     * Accepter un devis et valider le prestataire.
     */
    void acceptQuote(Long interventionId, Long quoteId);

    /**
     * Payer un acompte pour une intervention validée.
     */
    PaymentResponseDTO payerAcompte(Long interventionId, PayerAcompteDTO dto);

    /**
     * Valider les travaux et payer le solde.
     */
    PaymentResponseDTO validerEtPayerSolde(Long interventionId, ValiderTravauxDTO dto);
}
