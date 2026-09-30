package com.example.solimus.services.shared;

import com.example.solimus.repositories.SyndicWalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Calcul centralisé de la "trésorerie disponible" d'un syndic — seule source de vérité, réutilisée
// partout où ce chiffre est affiché (dashboards, wallet) et où il sert de garde-fou (validation d'un
// retrait). Même principe côté prestataire (WalletBalanceServiceImpl.getCurrentBalance) : si l'un
// des deux évolue, penser à répercuter sur l'autre.
@Component
@RequiredArgsConstructor
public class SyndicTreasuryService {

    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;

    // Trésorerie disponible à aujourd'hui = somme des SyndicWalletTransaction du wallet (CHARGES et
    // BUDGET_EXPENSE en positif/négatif, WITHDRAWAL en négatif une fois le retrait validé COMPLETED).
    // Un retrait encore PENDING ne crée aucune transaction, donc ne réserve rien.
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