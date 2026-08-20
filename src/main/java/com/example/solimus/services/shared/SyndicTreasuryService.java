package com.example.solimus.services.shared;

import com.example.solimus.repositories.SyndicWalletTransactionRepository;
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

    // Trésorerie disponible À AUJOURD'HUI = somme de toutes les SyndicWalletTransaction du wallet
    // jusqu'à maintenant (CHARGES et TRAVAUX en positif/négatif, RETRAIT en négatif dès qu'un retrait
    // est validé COMPLETED — voir WithdrawalRequestServiceImpl.validateWithdrawalRequest). Les retraits
    // encore PENDING ne créent aucune transaction, donc ne réservent rien : le blocage se fait au
    // moment de la validation, pas à la création de la demande.
    public BigDecimal getAvailableBalance(Long walletId, Long residenceId) {
        return getAvailableBalanceAsOf(walletId, residenceId, LocalDateTime.now());
    }

    // Même calcul, mais à une date passée précise (ex: un point du graphique "Évolution financière",
    // un mois donné) — ne compte que les transactions antérieures à cette date
    public BigDecimal getAvailableBalanceAsOf(Long walletId, Long residenceId, LocalDateTime asOfDate) {

        if (walletId == null) {
            return BigDecimal.ZERO;
        }

        return (residenceId != null)
                ? syndicWalletTransactionRepository.sumAllByResidenceId(residenceId, asOfDate)
                : syndicWalletTransactionRepository.sumTransactionsUpTo(walletId, asOfDate);
    }
}