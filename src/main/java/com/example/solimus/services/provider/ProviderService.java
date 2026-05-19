package com.example.solimus.services.provider;

import com.example.solimus.dtos.intervention.CreateQuoteDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.InterventionRequestSummaryDTO;
import com.example.solimus.dtos.provider.*;
import com.example.solimus.enums.InterventionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ProviderService {

    // Listing paginé et filtré pour le dashboard mobile
    Page<InterventionRequestSummaryDTO> getAvailableRequests(String search, InterventionStatus status, Pageable pageable);

    // Nombre total de demandes reçues par le prestataire
    long getTotalRequestsCount();

    // Détail complet d'une demande (quand on clique)
    InterventionRequestDTO getRequestDetails(Long id);

    List<InterventionRequestDTO> getMyInterventions();

    void createQuote(CreateQuoteDTO dto);

    // Démarrer une intervention (Prestataire)
    void startIntervention(Long requestId);

    void ajouterPhotoTravaux(Long requestId, org.springframework.web.multipart.MultipartFile photo);

    void ajouterCommentaire(Long requestId, String commentaire);

    void terminerIntervention(Long requestId);

    // =========================================================================
    // PROFIL PRESTATAIRE
    // =========================================================================
    ProviderProfileDTO getMyProfile();

    void toggleAvailability();

    // Informations personnelles (Édition de profil)
    UpdateProviderProfileDTO getPersonalInformation();
    
    void updateProfile(UpdateProviderProfileDTO dto, org.springframework.web.multipart.MultipartFile photo);

    // =========================================================================
    // MES DEVIS
    // =========================================================================
    ProviderQuoteListDTO getMesDevis(com.example.solimus.enums.QuoteStatus statut, String search, int page, int size);

   QuoteDetailDTO getQuoteDetails(Long quoteId);

    // =========================================================================
    // PORTEFEUILLE (WALLET)
    // =========================================================================
   WalletDTO getMonWallet();

   WithdrawalRequestDTO demanderVersement(DemanderVersementDTO dto);

   void crediterWallet(Long providerId, BigDecimal montant);

    // =========================================================================
    // TABLEAU DE BORD (DASHBOARD)
    // =========================================================================
    ProviderDashboardDTO getDashboard();
}
