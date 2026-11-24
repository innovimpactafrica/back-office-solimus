package com.example.solimus.services.platform;

import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.PlatformWalletTransaction;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.SyndicFinancialSettings;
import com.example.solimus.entities.User;
import com.example.solimus.enums.CommissionSource;
import com.example.solimus.enums.PlatformWalletTransactionCategory;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.PlatformWalletTransactionRepository;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import com.example.solimus.repositories.SyndicFinancialSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Commission Solimus sur les travaux — modèle « compte prépayé ».
 * <p>
 * Taux : celui du syndic de la résidence (paramètres financiers, {@code travauxCommissionRate}), sinon le
 * taux par défaut {@code app.travaux.commission.default-rate}. Commission = montant TTC du devis × taux / 100,
 * arrondie au franc (FCFA).
 * <ol>
 *   <li><b>Devis</b> : un prestataire ne peut envoyer un devis que si son wallet couvre la commission de ce
 *       devis ({@link #assertProviderCanCover}).</li>
 *   <li><b>Fin des travaux</b> : quand le prestataire termine, la commission est débitée automatiquement de
 *       son wallet et créditée au wallet Solimus ({@link #chargeOnCompletion}), une seule fois par intervention,
 *       quel que soit le moyen de paiement du client (espèces, Wave, virement…).</li>
 *   <li><b>Paiements</b> : aucune commission n'est retenue sur les paiements eux-mêmes ; un paiement qui
 *       transite par Solimus est crédité en entier au prestataire ({@link #creditProvider}).</li>
 * </ol>
 * Désactivable : {@code app.travaux.commission.enabled=false}.
 */
@Slf4j
@Service
public class TravauxCommissionService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SyndicFinancialSettingsRepository financialSettingsRepository;
    private final PlatformWalletTransactionRepository platformWalletTransactionRepository;
    private final ProviderWalletRepository providerWalletRepository;
    private final ProviderWalletTransactionRepository providerWalletTransactionRepository;
    private final BigDecimal defaultRate;
    private final boolean enabled;

    public TravauxCommissionService(SyndicFinancialSettingsRepository financialSettingsRepository,
                                    PlatformWalletTransactionRepository platformWalletTransactionRepository,
                                    ProviderWalletRepository providerWalletRepository,
                                    ProviderWalletTransactionRepository providerWalletTransactionRepository,
                                    @Value("${app.travaux.commission.default-rate:0}") BigDecimal defaultRate,
                                    @Value("${app.travaux.commission.enabled:true}") boolean enabled) {
        this.financialSettingsRepository = financialSettingsRepository;
        this.platformWalletTransactionRepository = platformWalletTransactionRepository;
        this.providerWalletRepository = providerWalletRepository;
        this.providerWalletTransactionRepository = providerWalletTransactionRepository;
        this.defaultRate = clampRate(defaultRate);
        this.enabled = enabled;
    }

    /** Répartition : montant de base, taux appliqué, commission, reste. */
    public record Split(BigDecimal gross, BigDecimal rate, BigDecimal commission, BigDecimal net) {
        public boolean hasCommission() {
            return commission.signum() > 0;
        }
    }

    /** Taux applicable à cette intervention (syndic de la résidence), en %. 0 si la commission est désactivée. */
    public BigDecimal rateFor(InterventionRequest request) {
        if (!enabled) {
            return BigDecimal.ZERO;
        }
        User syndic = syndicOf(request);
        if (syndic == null) {
            return defaultRate;
        }
        return financialSettingsRepository.findBySyndicId(syndic.getId())
                .map(SyndicFinancialSettings::getTravauxCommissionRate)
                .map(TravauxCommissionService::clampRate)
                .orElse(defaultRate);
    }

    public Split split(InterventionRequest request, BigDecimal gross) {
        BigDecimal amount = gross == null ? BigDecimal.ZERO : gross;
        BigDecimal rate = rateFor(request);
        BigDecimal commission = amount.signum() <= 0 ? BigDecimal.ZERO
                : amount.multiply(rate).divide(HUNDRED, 0, RoundingMode.HALF_UP);
        if (commission.compareTo(amount) > 0) {
            commission = amount;
        }
        return new Split(amount, rate, commission, amount.subtract(commission));
    }

    /** Solde actuel du wallet du prestataire (0 s'il n'en a pas encore). */
    public BigDecimal walletBalanceOf(Long providerId) {
        return providerWalletRepository.findByProviderId(providerId)
                .map(wallet -> providerWalletTransactionRepository.sumTransactionsUpTo(wallet.getId(), LocalDateTime.now()))
                .map(balance -> balance == null ? BigDecimal.ZERO : balance)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Refuse un devis dont la commission dépasse le solde du wallet du prestataire.
     * Appelé à l'envoi (et à la modification) d'un devis, sur son montant TTC.
     */
    public void assertProviderCanCover(User provider, InterventionRequest request, BigDecimal quoteTotal) {
        Split split = split(request, quoteTotal);
        if (!split.hasCommission()) {
            return;
        }
        BigDecimal balance = walletBalanceOf(provider.getId());
        if (balance.compareTo(split.commission()) < 0) {
            BigDecimal missing = split.commission().subtract(balance);
            throw new BadRequestException("Solde insuffisant : la commission Solimus de ce devis est de "
                    + split.commission().toPlainString() + " FCFA (" + rateLabel(split.rate()) + " de "
                    + split.gross().toPlainString() + " FCFA) et votre wallet contient " + balance.toPlainString()
                    + " FCFA. Rechargez votre compte d'au moins " + missing.toPlainString() + " FCFA pour envoyer ce devis.");
        }
    }

    /**
     * Fin des travaux : débite la commission du wallet du prestataire sélectionné et la crédite au wallet
     * Solimus. Une seule fois par intervention (idempotent). Base : montant TTC du devis accepté.
     * Le débit passe même si le solde est devenu insuffisant (solde négatif) : le prestataire ne voit alors
     * plus de nouvelles demandes tant qu'il n'a pas rechargé.
     */
    @Transactional
    public Split chargeOnCompletion(InterventionRequest request) {
        User provider = request != null ? request.getSelectedProvider() : null;
        Split split = split(request, request != null ? request.getTotalAmount() : null);
        if (provider == null || !split.hasCommission()) {
            return split;
        }
        // Verrou du wallet AVANT le contrôle d'unicité : deux fins de travaux simultanées sont sérialisées
        ProviderWallet wallet = providerWalletRepository.findByProviderIdForUpdate(provider.getId())
                .orElseGet(() -> providerWalletRepository.save(ProviderWallet.builder().provider(provider).build()));
        if (request.getId() != null && platformWalletTransactionRepository
                .existsByInterventionRequestIdAndCategory(request.getId(), PlatformWalletTransactionCategory.COMMISSION_TRAVAUX)) {
            log.info("Commission déjà prélevée pour l'intervention {} : aucun nouveau débit", request.getId());
            return split;
        }

        ProviderWalletTransaction debit = new ProviderWalletTransaction();
        debit.setWallet(wallet);
        // Catégorie existante réutilisée (montant négatif) : l'historique affiche la commission
        // depuis le wallet Solimus (platform_wallet_transactions)
        debit.setCategory(ProviderWalletTransactionCategory.TRAVAUX);
        debit.setAmount(split.commission().negate());
        debit.setLabel(truncate("Commission Solimus " + rateLabel(split.rate()) + " — " + titleOf(request)));
        debit.setReference(request.getReference());
        debit.setInterventionRequest(request);
        debit.setTransactionDate(LocalDateTime.now());
        providerWalletTransactionRepository.save(debit);

        platformWalletTransactionRepository.save(PlatformWalletTransaction.builder()
                .category(PlatformWalletTransactionCategory.COMMISSION_TRAVAUX)
                .source(CommissionSource.FIN_DE_TRAVAUX)
                .amount(split.commission())
                .grossAmount(split.gross())
                .commissionRate(split.rate())
                .label(truncate("Commission " + rateLabel(split.rate()) + " — " + titleOf(request)))
                .paymentReference(request.getReference())
                .interventionRequest(request)
                .provider(provider)
                .syndic(syndicOf(request))
                .transactionDate(LocalDateTime.now())
                .build());
        log.info("Commission Solimus {} FCFA ({} de {} FCFA) débitée du prestataire {} — intervention {}",
                split.commission(), rateLabel(split.rate()), split.gross(), provider.getId(), request.getId());
        return split;
    }

    /**
     * Paiement de travaux qui transite par Solimus (en ligne, ou wallet du syndic) : crédité EN ENTIER au
     * prestataire — la commission est prélevée une seule fois, à la fin des travaux. Le wallet doit déjà
     * être verrouillé par l'appelant.
     */
    @Transactional
    public void creditProvider(ProviderWallet wallet, InterventionRequest request, BigDecimal amount,
                               ProviderWalletTransactionCategory category, String label, String paymentReference) {
        ProviderWalletTransaction credit = new ProviderWalletTransaction();
        credit.setWallet(wallet);
        credit.setCategory(category);
        credit.setAmount(amount);
        credit.setLabel(truncate(label));
        credit.setReference(paymentReference);
        credit.setInterventionRequest(request);
        credit.setTransactionDate(LocalDateTime.now());
        providerWalletTransactionRepository.save(credit);
    }

    private static String titleOf(InterventionRequest request) {
        return request != null && request.getTitle() != null ? request.getTitle() : "Travaux";
    }

    private static String rateLabel(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString() + " %";
    }

    private static User syndicOf(InterventionRequest request) {
        return request != null && request.getResidence() != null ? request.getResidence().getSyndic() : null;
    }

    private static BigDecimal clampRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0) return BigDecimal.ZERO;
        return rate.min(HUNDRED);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 255 ? value : value.substring(0, 255);
    }
}
