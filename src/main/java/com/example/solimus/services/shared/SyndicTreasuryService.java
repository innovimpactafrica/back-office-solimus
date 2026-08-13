package com.example.solimus.services.shared;

import com.example.solimus.repositories.SyndicWalletTransactionRepository;
import com.example.solimus.repositories.SyndicWithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Calcul centralisé de la "trésorerie disponible" d'un syndic — seule source de vérité, réutilisée
// partout où ce chiffre est affiché (dashboards, wallet) ET là où il sert de garde-fou (validation
// d'un retrait), pour qu'ils ne puissent jamais diverger.
@Component
@RequiredArgsConstructor
public class SyndicTreasuryService {

    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final SyndicWithdrawalRequestRepository syndicWithdrawalRequestRepository;

    // Trésorerie disponible À AUJOURD'HUI = toutes les transactions du wallet jusqu'à maintenant, moins
    // UNIQUEMENT les retraits réellement COMPLETED. Les retraits encore PENDING ne réservent plus rien :
    // le blocage se fait désormais au moment de la validation (voir WithdrawalRequestServiceImpl), pas
    // à la création de la demande — donc deux demandes PENDING concurrentes peuvent chacune afficher
    // le même solde disponible tant qu'aucune des deux n'est validée.
    public BigDecimal getAvailableBalance(Long walletId, Long residenceId) {
        return getAvailableBalanceAsOf(walletId, residenceId, LocalDateTime.now());
    }

    // Même calcul, mais à une date passée précise (ex: un point du graphique "Évolution financière",
    // un mois donné) — ne compte que les transactions et les retraits COMPLETED antérieurs à cette date
    public BigDecimal getAvailableBalanceAsOf(Long walletId, Long residenceId, LocalDateTime asOfDate) {

        if (walletId == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal transactions = (residenceId != null)
                ? syndicWalletTransactionRepository.sumAllByResidenceId(residenceId, asOfDate)
                : syndicWalletTransactionRepository.sumTransactionsUpTo(walletId, asOfDate);

        BigDecimal retraitsCompletes = syndicWithdrawalRequestRepository
                .sumCompletedAmountUpTo(walletId, asOfDate, residenceId);

        return transactions.subtract(retraitsCompletes);
    }
}