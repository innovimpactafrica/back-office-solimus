package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.admin.SpecialtyDTO;
import com.example.solimus.dtos.intervention.*;
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
     * @param search Recherche par titre (optionnel)
     * @param status Filtre par statut (optionnel)
     * @param page Numéro de page (défaut 0)
     * @param size Taille de page (défaut 10)
     */
    com.example.solimus.dtos.intervention.OwnerInterventionPageDTO getMyInterventions(
            String search,
            com.example.solimus.enums.InterventionStatus status,
            int page,
            int size);

    /**
     * Récupère le détail d'une intervention spécifique du copropriétaire.
     */
    OwnerInterventionDetailDTO getInterventionDetail(Long interventionId);

    // =========================================================================
    // GESTION DES DEVIS — CÔTÉ COPROPRIÉTAIRE
    // =========================================================================

    /**
     * Retourne la liste des devis reçus pour une intervention,
     * triés par score qualité/prix décroissant (le recommandé en premier).
     * @param interventionId ID de l'intervention
     * @param page Numéro de page (défaut 0)
     * @param size Taille de page (défaut 10)
     */
    org.springframework.data.domain.Page<CoOwnerQuoteCardDTO> getQuotesByIntervention(
            Long interventionId,
            int page,
            int size);

    /**
     * Retourne le détail complet d'un devis (contact, lignes matériaux, main d'œuvre).
     */
    CoOwnerQuoteDetailDTO getQuoteDetail(Long interventionId, Long quoteId);

    /**
     * Retourne le nombre total de devis reçus pour une intervention.
     * Utilisé pour afficher "Vous avez reçu X devis".
     */
    int getQuotesCount(Long interventionId);

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
