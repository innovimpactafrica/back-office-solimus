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
 * Commission Solimus sur les paiements de travaux.
 * <p>
 * Taux : celui du syndic de la résidence de l'intervention (paramètres financiers,
 * {@code travauxCommissionRate}), sinon le taux par défaut {@code app.travaux.commission.default-rate}.
 * Commission = montant × taux / 100, arrondie au franc (FCFA, sans décimale).
 * <ul>
 *   <li><b>Paiement qui transite par Solimus</b> (en ligne, ou wallet du syndic) : le wallet du prestataire
 *       est crédité du montant NET (montant − commission) ; la commission est créditée au wallet plateforme.</li>
 *   <li><b>Paiement hors plateforme</b> (preuve confirmée par le prestataire) : l'argent n'a pas transité
 *       par Solimus, le prestataire l'a reçu en entier. La commission est DÉBITÉE de son wallet (ligne
 *       négative) et créditée au wallet plateforme — d'où le solde minimum exigé pour voir les demandes.
 *       Désactivable : {@code app.travaux.commission.off-platform.enabled=false}.</li>
 * </ul>
 * Toujours appelé dans la transaction du paiement (même verrou, même commit) : une commission n'existe
 * que si le paiement est enregistré, et les chemins appelants sont déjà idempotents.
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
    private final boolean offPlatformEnabled;

    public TravauxCommissionService(SyndicFinancialSettingsRepository financialSettingsRepository,
                                    PlatformWalletTransactionRepository platformWalletTransactionRepository,
                                    ProviderWalletRepository providerWalletRepository,
                                    ProviderWalletTransactionRepository providerWalletTransactionRepository,
                                    @Value("${app.travaux.commission.default-rate:0}") BigDecimal defaultRate,
                                    @Value("${app.travaux.commission.off-platform.enabled:true}") boolean offPlatformEnabled) {
        this.financialSettingsRepository = financialSettingsRepository;
        this.platformWalletTransactionRepository = platformWalletTransactionRepository;
        this.providerWalletRepository = providerWalletRepository;
        this.providerWalletTransactionRepository = providerWalletTransactionRepository;
        this.defaultRate = clampRate(defaultRate);
        this.offPlatformEnabled = offPlatformEnabled;
    }

    /** Répartition d'un paiement : montant brut, taux appliqué, commission, net pour le prestataire. */
    public record Split(BigDecimal gross, BigDecimal rate, BigDecimal commission, BigDecimal net) {
        public boolean hasCommission() {
            return commission.signum() > 0;
        }
    }

    /** Taux applicable à cette intervention (syndic de la résidence), en %. */
    public BigDecimal rateFor(InterventionRequest request) {
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

    /**
     * Paiement qui transite par Solimus : crédite le wallet du prestataire du NET et le wallet plateforme
     * de la commission. Le wallet doit déjà être verrouillé par l'appelant.
     *
     * @return la répartition appliquée (net crédité = {@code split.net()})
     */
    @Transactional
    public Split creditProviderNet(ProviderWallet wallet, InterventionRequest request, BigDecimal gross,
                                   ProviderWalletTransactionCategory category, String label,
                                   String paymentReference, CommissionSource source) {
        Split split = split(request, gross);

        ProviderWalletTransaction credit = new ProviderWalletTransaction();
        credit.setWallet(wallet);
        credit.setCategory(category);
        credit.setAmount(split.net());
        credit.setLabel(withCommissionNote(label, split));
        credit.setReference(paymentReference);
        credit.setInterventionRequest(request);
        credit.setTransactionDate(LocalDateTime.now());
        providerWalletTransactionRepository.save(credit);

        recordPlatformCommission(request, wallet.getProvider(), split, paymentReference, source);
        return split;
    }

    /**
     * Paiement hors plateforme confirmé : débite la commission du wallet du prestataire (créé au besoin)
     * et la crédite au wallet plateforme. Sans effet si le taux est nul ou si l'option est désactivée.
     */
    @Transactional
    public Split chargeOffPlatformCommission(User provider, InterventionRequest request, BigDecimal gross,
                                             String label, String paymentReference) {
        Split split = split(request, gross);
        if (!offPlatformEnabled || !split.hasCommission()) {
            return new Split(split.gross(), split.rate(), BigDecimal.ZERO, split.gross());
        }
        ProviderWallet wallet = providerWalletRepository.findByProviderIdForUpdate(provider.getId())
                .orElseGet(() -> providerWalletRepository.save(ProviderWallet.builder().provider(provider).build()));

        ProviderWalletTransaction debit = new ProviderWalletTransaction();
        debit.setWallet(wallet);
        // Catégorie existante réutilisée (montant négatif) : la colonne peut être un ENUM MySQL figé
        debit.setCategory(ProviderWalletTransactionCategory.TRAVAUX);
        debit.setAmount(split.commission().negate());
        debit.setLabel(truncate("Commission Solimus " + rateLabel(split.rate()) + " — " + label));
        debit.setReference(paymentReference);
        debit.setInterventionRequest(request);
        debit.setTransactionDate(LocalDateTime.now());
        providerWalletTransactionRepository.save(debit);

        recordPlatformCommission(request, provider, split, paymentReference, CommissionSource.HORS_PLATEFORME);
        return split;
    }

    private void recordPlatformCommission(InterventionRequest request, User provider, Split split,
                                          String paymentReference, CommissionSource source) {
        if (!split.hasCommission()) {
            return;
        }
        platformWalletTransactionRepository.save(PlatformWalletTransaction.builder()
                .category(PlatformWalletTransactionCategory.COMMISSION_TRAVAUX)
                .source(source)
                .amount(split.commission())
                .grossAmount(split.gross())
                .commissionRate(split.rate())
                .label(truncate("Commission " + rateLabel(split.rate()) + " — "
                        + (request != null && request.getTitle() != null ? request.getTitle() : "Travaux")))
                .paymentReference(paymentReference)
                .interventionRequest(request)
                .provider(provider)
                .syndic(syndicOf(request))
                .transactionDate(LocalDateTime.now())
                .build());
        log.info("Commission Solimus {} FCFA ({} sur {} FCFA, {}) — intervention {}", split.commission(),
                rateLabel(split.rate()), split.gross(), source, request != null ? request.getId() : null);
    }

    private static String withCommissionNote(String label, Split split) {
        if (!split.hasCommission()) {
            return truncate(label);
        }
        return truncate(label + " (commission " + rateLabel(split.rate()) + " : "
                + split.commission().toPlainString() + " FCFA)");
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
