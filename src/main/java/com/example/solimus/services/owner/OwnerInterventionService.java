package com.example.solimus.services.owner;

import com.example.solimus.dtos.intervention.*;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;

public interface OwnerInterventionService {



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

    CoOwnerQuoteDetailDTO getQuoteDetail(Long interventionId, Long quoteId); **/

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
