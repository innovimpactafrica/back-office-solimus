package com.example.solimus.services.provider.eligibility;

import com.example.solimus.dtos.provider.eligibility.ProviderEligibilityDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.services.provider.wallet.WalletBalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Règle d'accès aux demandes de travaux : seul un prestataire dont le solde de wallet est
 * STRICTEMENT supérieur au seuil (1000 FCFA par défaut, {@code PROVIDER_MIN_WALLET_BALANCE}) voit
 * les demandes, est inclus dans leur diffusion et peut envoyer un devis. Ses travaux déjà attribués
 * restent accessibles quel que soit son solde.
 */
@Slf4j
@Service
public class ProviderEligibilityService {

    private final WalletBalanceService walletBalanceService;
    private final BigDecimal minimumBalanceExclusive;

    public ProviderEligibilityService(WalletBalanceService walletBalanceService,
                                      @Value("${app.provider.min-wallet-balance:1000}") BigDecimal minimumBalanceExclusive) {
        this.walletBalanceService = walletBalanceService;
        this.minimumBalanceExclusive = minimumBalanceExclusive;
    }

    public BigDecimal getMinimumBalanceExclusive() {
        return minimumBalanceExclusive;
    }

    @Transactional(readOnly = true)
    public boolean isEligible(Long providerId) {
        return balanceOf(providerId).compareTo(minimumBalanceExclusive) > 0;
    }

    @Transactional(readOnly = true)
    public ProviderEligibilityDTO getEligibility(User provider) {
        BigDecimal balance = balanceOf(provider.getId());
        boolean eligible = balance.compareTo(minimumBalanceExclusive) > 0;
        // Solde en FCFA (sans centimes) : il faut au moins seuil + 1
        BigDecimal missing = eligible ? BigDecimal.ZERO
                : minimumBalanceExclusive.subtract(balance).add(BigDecimal.ONE).max(BigDecimal.ZERO);
        return ProviderEligibilityDTO.builder()
                .eligible(eligible)
                .walletBalance(balance)
                .minimumBalanceExclusive(minimumBalanceExclusive)
                .missingAmount(missing)
                .message(eligible
                        ? "Vous avez accès aux demandes de travaux."
                        : "Solde insuffisant : votre wallet doit dépasser " + minimumBalanceExclusive.toPlainString()
                          + " FCFA pour voir les demandes de travaux (il manque " + missing.toPlainString() + " FCFA).")
                .build();
    }

    /** 403 avec le message d'éligibilité si le solde est insuffisant. */
    @Transactional(readOnly = true)
    public void assertEligible(User provider) {
        ProviderEligibilityDTO eligibility = getEligibility(provider);
        if (!eligibility.isEligible()) {
            throw new ForbiddenException(eligibility.getMessage());
        }
    }

    private BigDecimal balanceOf(Long providerId) {
        BigDecimal balance = walletBalanceService.getCurrentBalance(providerId);
        return balance != null ? balance : BigDecimal.ZERO;
    }
}
