package com.example.solimus.services.syndic.finance;

import com.example.solimus.dtos.syndic.finance.FinanceDashboardDTO;
import com.example.solimus.dtos.syndic.finance.FinancePaymentRowDTO;
import com.example.solimus.dtos.syndic.finance.RecentPaymentDTO;
import com.example.solimus.dtos.syndic.finance.UnpaidListResponse;

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
     * Liste des derniers paiements reçus (toutes résidences du syndic)
     */
    List<RecentPaymentDTO> getRecentPayments(int limit);

    //--------------------------------------------------
    // ===== PAIEMENTS (module Finances, historique complet) =====
    //--------------------------------------------------

    /**
     * Liste paginée des paiements de charges du module Finances (historique global, toutes résidences)
     */
    Page<FinancePaymentRowDTO> getFinancePayments(int page, int size);

    //--------------------------------------------------
    // ===== IMPAYÉS (module Finances, historique complet) =====
    //--------------------------------------------------

    /**
     * Liste paginée des impayés du module Finances (historique global, toutes résidences)
     */
    UnpaidListResponse getFinanceUnpaid(int page, int size);
}
