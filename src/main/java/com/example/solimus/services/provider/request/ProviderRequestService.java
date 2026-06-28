package com.example.solimus.services.provider.request;

import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.provider.request.*;
import com.example.solimus.enums.ProviderRequestDisplayStatus;
import com.example.solimus.enums.QuoteStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProviderRequestService {

    /**
     * Retourne la liste paginée des demandes d'intervention notifiées
     * au prestataire connecté, mais qui ne lui sont pas (encore) définitivement
     * assignées — c'est-à-dire l'écran "Demandes", pas l'écran "Travaux".
     *
     * @param filterStatus statut affiché à filtrer (REJECTED, QUOTE_SENT, PENDING_QUOTE).
     *                      Si null, retourne toutes les demandes, peu importe leur statut.
     * @param search       recherche par titre de la demande ou nom de la résidence
     * @param pageable     informations de pagination (page, taille, tri)
     * @return un ProviderRequestsDTO contenant le total des demandes reçues et la page filtrée
     */
    ProviderRequestsDTO getAvailableRequests(
            ProviderRequestDisplayStatus filterStatus,
            String search,
            Pageable pageable);

    /**
     * Retourne les détails d'une demande pour le prestataire connecté
     */
    ProviderRequestDetailDTO getRequestDetails(Long requestId);

    /** Créer un devis (brouillon ou envoyé)*/
    void createQuote(CreateQuoteDTO dto);

    /** Lister mes devis (avec filtres et pagination)*/
    ProviderQuoteListDTO getMyQuotes(QuoteStatus statut, String search, int page, int size);

    /** Voir le détail d'un devis*/
    QuoteDetailDTO getQuoteDetails(Long quoteId);

    /** Lister les délais d'estimation disponibles*/
    List<EstimatedDelayDTO> getEstimatedDelays();
}