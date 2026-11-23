package com.example.solimus.services.platform;

import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.PlatformWalletTransaction;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.SyndicFinancialSettings;
import com.example.solimus.entities.User;
import com.example.solimus.enums.CommissionSource;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.repositories.PlatformWalletTransactionRepository;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import com.example.solimus.repositories.SyndicFinancialSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Commission Solimus : taux du syndic, net crédité au prestataire, commission au wallet plateforme. */
class TravauxCommissionServiceTest {

    private final SyndicFinancialSettingsRepository settingsRepository = mock(SyndicFinancialSettingsRepository.class);
    private final PlatformWalletTransactionRepository platformRepository = mock(PlatformWalletTransactionRepository.class);
    private final ProviderWalletRepository walletRepository = mock(ProviderWalletRepository.class);
    private final ProviderWalletTransactionRepository ledger = mock(ProviderWalletTransactionRepository.class);

    private User provider;
    private InterventionRequest request;
    private ProviderWallet wallet;

    @BeforeEach
    void setUp() {
        User syndic = new User();
        syndic.setId(65L);
        provider = new User();
        provider.setId(9L);
        Residence residence = new Residence();
        residence.setSyndic(syndic);
        request = new InterventionRequest();
        request.setId(51L);
        request.setTitle("Fuite hall");
        request.setResidence(residence);
        wallet = ProviderWallet.builder().provider(provider).build();
        when(walletRepository.findByProviderIdForUpdate(9L)).thenReturn(Optional.of(wallet));
    }

    private TravauxCommissionService service(String syndicRate, String defaultRate, boolean offPlatform) {
        SyndicFinancialSettings settings = new SyndicFinancialSettings();
        settings.setTravauxCommissionRate(syndicRate == null ? null : new BigDecimal(syndicRate));
        when(settingsRepository.findBySyndicId(65L)).thenReturn(Optional.of(settings));
        return new TravauxCommissionService(settingsRepository, platformRepository, walletRepository, ledger,
                new BigDecimal(defaultRate), offPlatform);
    }

    @Test
    void creditsTheNetToTheProviderAndTheCommissionToThePlatform() {
        TravauxCommissionService.Split split = service("5", "0", true).creditProviderNet(wallet, request,
                new BigDecimal("47200"), ProviderWalletTransactionCategory.TRAVAUX, "Acompte — Fuite hall",
                "PAY-000001", CommissionSource.WALLET_SYNDIC);

        assertThat(split.commission()).isEqualByComparingTo("2360");
        assertThat(split.net()).isEqualByComparingTo("44840");

        ArgumentCaptor<ProviderWalletTransaction> credit = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(ledger).save(credit.capture());
        assertThat(credit.getValue().getAmount()).isEqualByComparingTo("44840");
        assertThat(credit.getValue().getLabel()).contains("commission 5 %").contains("2360 FCFA");

        ArgumentCaptor<PlatformWalletTransaction> platform = ArgumentCaptor.forClass(PlatformWalletTransaction.class);
        verify(platformRepository).save(platform.capture());
        assertThat(platform.getValue().getAmount()).isEqualByComparingTo("2360");
        assertThat(platform.getValue().getGrossAmount()).isEqualByComparingTo("47200");
        assertThat(platform.getValue().getSource()).isEqualTo(CommissionSource.WALLET_SYNDIC);
        assertThat(platform.getValue().getSyndic().getId()).isEqualTo(65L);
    }

    @Test
    void usesThePlatformDefaultWhenTheSyndicHasNoRate_andRoundsToTheFranc() {
        TravauxCommissionService.Split split = service(null, "2.5", true).split(request, new BigDecimal("1001"));

        assertThat(split.rate()).isEqualByComparingTo("2.5");
        assertThat(split.commission()).isEqualByComparingTo("25"); // 25,025 arrondi au franc
        assertThat(split.net()).isEqualByComparingTo("976");
    }

    @Test
    void zeroRate_creditsEverythingAndRecordsNoCommission() {
        service("0", "0", true).creditProviderNet(wallet, request, new BigDecimal("10000"),
                ProviderWalletTransactionCategory.INTERVENTION_PAYMENT, "Acompte", "PAY-1", CommissionSource.PAIEMENT_EN_LIGNE);

        ArgumentCaptor<ProviderWalletTransaction> credit = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(ledger).save(credit.capture());
        assertThat(credit.getValue().getAmount()).isEqualByComparingTo("10000");
        verify(platformRepository, never()).save(any());
    }

    @Test
    void offPlatformPayment_debitsTheCommissionFromTheProviderWallet() {
        service("10", "0", true).chargeOffPlatformCommission(provider, request, new BigDecimal("20000"),
                "Solde final — Fuite hall", "SOL-000002");

        ArgumentCaptor<ProviderWalletTransaction> debit = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(ledger).save(debit.capture());
        assertThat(debit.getValue().getAmount()).isEqualByComparingTo("-2000");
        ArgumentCaptor<PlatformWalletTransaction> platform = ArgumentCaptor.forClass(PlatformWalletTransaction.class);
        verify(platformRepository).save(platform.capture());
        assertThat(platform.getValue().getAmount()).isEqualByComparingTo("2000");
        assertThat(platform.getValue().getSource()).isEqualTo(CommissionSource.HORS_PLATEFORME);
    }

    @Test
    void offPlatformCommissionCanBeDisabled() {
        service("10", "0", false).chargeOffPlatformCommission(provider, request, new BigDecimal("20000"), "Solde", "SOL-1");

        verify(ledger, never()).save(any());
        verify(platformRepository, never()).save(any());
    }
}
