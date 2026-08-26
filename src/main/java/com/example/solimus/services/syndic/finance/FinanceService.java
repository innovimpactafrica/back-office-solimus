package com.example.solimus.services.syndic.finance;

import com.example.solimus.dtos.shared.ExcelFileDTO;
import com.example.solimus.dtos.syndic.charge.PaymentListResponse;
import com.example.solimus.dtos.syndic.dashboard.TreasuryEvolutionPointDTO;
import com.example.solimus.dtos.syndic.finance.FinanceDashboardDTO;
import com.example.solimus.dtos.syndic.finance.RecentPaymentDTO;
import com.example.solimus.dtos.syndic.finance.UnpaidListResponse;
import com.example.solimus.dtos.syndic.residence.WalletTransactionDTO;
import com.example.solimus.enums.WalletTransactionCategory;

import org.springframework.data.domain.Page;

import java.util.List;

public interface FinanceService {

    //--------------------------------------------------
    // ===== DASHBOARD "FINANCES" =====
    //--------------------------------------------------

    /**
     * Dashboard "Finances" — trésorerie, charges collectées, impayés, dépenses + graphique cumulatif
     */
    FinanceDashboardDTO getFinanceDashboard();

    /**
     * Graphique cumulatif "Trésorerie vs Appels de charges" sur les 6 derniers mois glissants.
     * residenceId est OPTIONNEL : si fourni, filtre sur cette résidence ;
     * si absent, calcule sur toutes les résidences du syndic (wallet global).
     */
    List<TreasuryEvolutionPointDTO> getTreasuryEvolution(Long residenceId);

    /**
     * Liste des derniers paiements reçus (toutes résidences du syndic)
     */
    List<RecentPaymentDTO> getRecentPayments(int limit);

    //--------------------------------------------------
    // ===== PAIEMENTS (module Finances, historique complet) =====
    //--------------------------------------------------

    /**
     * Liste paginée des paiements de charges du module Finances, alignée intégralement sur
     * /api/syndic/budget/payments (mêmes champs, mêmes filtres résidence/année/recherche)
     */
    PaymentListResponse getFinancePayments(Long residenceId, Integer year, int page, int size, String search);

    //--------------------------------------------------
    // ===== IMPAYÉS (module Finances, historique complet) =====
    //--------------------------------------------------

    /**
     * Liste paginée des impayés du module Finances, alignée sur /api/syndic/budget/unpaid
     * (mêmes champs + filtres résidence/année), avec en plus la date d'échéance (dueDate)
     */
    UnpaidListResponse getFinanceUnpaid(Long residenceId, Integer year, int page, int size);

    //--------------------------------------------------
    // ===== TRANSACTIONS WALLET (historique complet, "Voir l'historique complet") =====
    //--------------------------------------------------

    /**
     * Historique paginé complet des transactions du wallet syndic (toutes résidences, toutes
     * catégories), avec filtres optionnels par catégorie et par année
     */
    Page<WalletTransactionDTO> getWalletTransactions(Long residenceId, WalletTransactionCategory category, Integer year, int page, int size);

    //--------------------------------------------------
    // ===== EXPORTS EXCEL (mêmes filtres que la liste paginée correspondante, sans pagination) =====
    //--------------------------------------------------

    /**
     * Export .xlsx de tous les paiements correspondant aux filtres (résidence/année/recherche) —
     * pas de pagination, toutes les lignes filtrées
     */
    ExcelFileDTO exportPayments(Long residenceId, Integer year, String search);

    /**
     * Export .xlsx de tous les impayés correspondant aux filtres (résidence/année) —
     * pas de pagination, toutes les lignes filtrées
     */
    ExcelFileDTO exportUnpaid(Long residenceId, Integer year);

    /**
     * Export .xlsx de toutes les transactions du wallet correspondant aux filtres
     * (résidence/catégorie/année) — pas de pagination, toutes les lignes filtrées
     */
    ExcelFileDTO exportWalletTransactions(Long residenceId, WalletTransactionCategory category, Integer year);
}
