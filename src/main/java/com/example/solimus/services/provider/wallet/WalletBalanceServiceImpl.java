package com.example.solimus.services.provider.wallet;

import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import com.example.solimus.repositories.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WalletBalanceServiceImpl implements WalletBalanceService {

    private final ProviderWalletRepository providerWalletRepository;
    private final ProviderWalletTransactionRepository transactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getCurrentBalance(Long providerId) {

        ProviderWallet wallet = findWalletOrNull(providerId);
        if (wallet == null) {
            return BigDecimal.ZERO;
        }

        // Total encaissé jusqu'à maintenant, moins ce qui est réservé par des retraits en attente
        // ou déjà validés (jamais compté deux fois : un retrait COMPLETED reste "réservé" ici, il
        // n'a jamais été remis dans le total encaissé)
        BigDecimal totalReceived = transactionRepository.sumTransactionsUpTo(wallet.getId(), LocalDateTime.now());
        BigDecimal reserved = withdrawalRequestRepository.sumPendingAndCompletedByProviderId(providerId);

        return totalReceived.subtract(reserved);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalanceAtDate(Long providerId, LocalDateTime asOfDate) {

        ProviderWallet wallet = findWalletOrNull(providerId);
        if (wallet == null) {
            return BigDecimal.ZERO;
        }

        // Solde brut à cette date — pas de déduction des retraits ici, uniquement utilisé pour
        // comparer une évolution dans le temps, jamais pour vérifier un accès
        return transactionRepository.sumTransactionsUpTo(wallet.getId(), asOfDate);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalThisMonth(Long providerId) {

        ProviderWallet wallet = findWalletOrNull(providerId);
        if (wallet == null) {
            return BigDecimal.ZERO;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();

        return transactionRepository.sumInPeriod(wallet.getId(), startOfMonth, now);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getWithdrawnThisMonth(Long providerId) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();

        return withdrawalRequestRepository.sumCompletedAmountInPeriod(providerId, startOfMonth, now);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getPendingBalance(Long providerId) {
        return withdrawalRequestRepository.sumPendingByProviderId(providerId);
    }

    //Méthode utilitaire
    private ProviderWallet findWalletOrNull(Long providerId) {
        return providerWalletRepository.findByProviderId(providerId).orElse(null);
    }
}