package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.intervention.CoOwnerQuoteCardDTO;
import com.example.solimus.dtos.syndic.travaux.PayDepositDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.owner.travaux.ValiderTravauxDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.owner.travaux.BalanceSummaryDTO;
import com.example.solimus.dtos.owner.travaux.CoOwnerQuoteDetailDTO;
import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.enums.InterventionStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ownerTraveauxService {

    /**
     * Lister toutes les résidences où le copropriétaire connecté possède un bien.
     */
    List<ResidenceDTO> getMyResidences();

    /**
     * Lister les parties communes d'une résidence où il a au moins un bien.
     */
    List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId);

    /**
     * Lister mes biens dans une résidence donnée.
     */
    List<PropertyDTO> getMyPropertiesByResidence(Long residenceId);

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================
    /**
     * Créer une demande d'intervention initiée par le copropriétaire.
     * - APPARTEMENT : managementMode = OWNER (géré par le copropriétaire)
     * - PARTIE_COMMUNE : managementMode = SYNDIC (géré par le syndic)
     */
    void createIntervention(CreateOwnerInterventionRequestDTO dto);

    // =========================================================================
    // LISTER MES DEMANDES DE TRAVAUX
    // =========================================================================
    /**
     * Lister, filtrer et paginer les demandes de travaux du copropriétaire connecté.
     * @param search      recherche par titre (optionnel)
     * @param status      filtre par statut (optionnel)
     * @param residenceId filtre par résidence (optionnel)
     * @param page        numéro de page (défaut 0)
     * @param size        taille de page (défaut 10)
     */
    OwnerInterventionDTO getMyInterventions(String search, InterventionStatus status, Long residenceId, int page, int size);

    /**
     * Récupérer les détails d'une intervention spécifique du copropriétaire.
     */
    OwnerInterventionDetailDTO getInterventionDetail(Long interventionId);

    /**
     * Créer un avis pour une intervention terminée.
     */
    void createReview(Long interventionId, CreateReviewDTO dto);

    // =========================================================================
    // GESTION DES DEVIS
    // =========================================================================

    /**
     * Retourne la liste des devis reçus pour une intervention,
     * triés par score qualité/prix (le recommandé en premier).
     * @param interventionId ID de l'intervention
     * @param page Numéro de page (défaut 0)
     * @param size Taille de page (défaut 10)
     */
    Page<CoOwnerQuoteCardDTO> getQuotesByIntervention(Long interventionId, int page, int size);

    /**
     * Récupérer les détails d'un devis spécifique pour une intervention.
     * @param interventionId ID de l'intervention
     * @param quoteId ID du devis
     */
    CoOwnerQuoteDetailDTO getQuoteDetail(Long interventionId, Long quoteId);

    /**
     * Accepter un devis et valider le prestataire.
     * @param interventionId ID de l'intervention
     * @param quoteId ID du devis à accepter
     */
    void acceptQuote(Long interventionId, Long quoteId);

    /**
     * Payer un acompte pour un devis validé.
     * @param interventionId ID de l'intervention
     * @param dto DTO contenant le montant et la méthode de paiement
     */
    PaymentResponseDTO payDeposit(Long interventionId, PayDepositDTO dto);

    /**
     * Valider les travaux et payer le solde.
     * @param interventionId ID de l'intervention
     * @param dto DTO contenant la méthode de paiement
     */
    PaymentResponseDTO validateAndPayBalance(Long interventionId, ValiderTravauxDTO dto);

    /**
     * Récupérer le récapitulatif financier (montant devis, acompte versé, solde restant)
     * pour afficher l'écran de paiement du solde avant validation.
     * @param interventionId ID de l'intervention
     */
    BalanceSummaryDTO getBalanceSummary(Long interventionId);

}
