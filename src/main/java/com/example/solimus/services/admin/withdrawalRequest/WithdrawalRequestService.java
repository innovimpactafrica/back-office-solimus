package com.example.solimus.services.admin.withdrawalRequest;

import com.example.solimus.dtos.admin.withdrawal.*;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface WithdrawalRequestService {

    // ===== Bloc 1 =====

    /**
     * KPIs de la page "Demandes de retraits" (Syndic + Prestataire confondus).
     */
    WithdrawalDashboardKpiDTO getDashboardKpis();

    // ===== Bloc 2 =====

    /**
     * Liste paginée des demandes de retrait (Syndic + Prestataire fusionnés), triée de la plus
     * récente à la plus ancienne, avec recherche et filtre par statut optionnels.
     */
    Page<WithdrawalRequestRowDTO> getAllWithdrawalRequests(String search, WithdrawalStatus status, int page, int size);

    // ===== Bloc 3 =====

    /**
     * Fiche détail complète d'une demande de retrait précise : en-tête, informations générales,
     * analyse financière, derniers retraits effectués et suivi (timeline).
     */
    WithdrawalRequestDetailDTO getWithdrawalRequestDetail(Long id, SubscriberType type);

    // ===== Bloc 4 =====

    /**
     * Récapitulatif affiché dans la modale de validation, avant confirmation par l'admin.
     */
    WithdrawalValidationSummaryDTO getValidationSummary(Long id, SubscriberType type);

    /**
     * Valide une demande de retrait : upload du reçu, passage au statut COMPLETED, notification optionnelle du
     * demandeur.
     */
    WithdrawalActionResultDTO validateWithdrawalRequest(Long id, SubscriberType type, MultipartFile receipt, String comment);

    // ===== Bloc 5 =====

    /**
     * Refuse une demande de retrait : passage au statut REJECTED, notification optionnelle du demandeur.
     */
    WithdrawalActionResultDTO rejectWithdrawalRequest(Long id, SubscriberType type, RejectWithdrawalRequestDTO dto);
}