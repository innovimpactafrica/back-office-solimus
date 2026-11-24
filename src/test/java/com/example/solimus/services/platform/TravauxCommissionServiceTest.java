package com.example.solimus.services.platform;

import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.PlatformWalletTransaction;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.Residence;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Commission Solimus : contrôle au devis, débit automatique du wallet prestataire à la fin des travaux. */
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
        request.setSelectedProvider(provider);
        request.setTotalAmount(new BigDecimal("47200"));
        wallet = ProviderWallet.builder().id(3L).provider(provider).build();
        when(walletRepository.findByProviderIdForUpdate(9L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByProviderId(9L)).thenReturn(Optional.of(wallet));
    }

    private TravauxCommissionService service(String syndicRate, String defaultRate, boolean enabled) {
        SyndicFinancialSettings settings = new SyndicFinancialSettings();
        settings.setTravauxCommissionRate(syndicRate == null ? null : new BigDecimal(syndicRate));
        when(settingsRepository.findBySyndicId(65L)).thenReturn(Optional.of(settings));
        return new TravauxCommissionService(settingsRepository, platformRepository, walletRepository, ledger,
                new BigDecimal(defaultRate), enabled);
    }

    private void balance(String amount) {
        when(ledger.sumTransactionsUpTo(eq(3L), any())).thenReturn(new BigDecimal(amount));
    }

    @Test
    void completion_debitsTheCommissionFromTheProviderAndCreditsSolimus() {
        TravauxCommissionService.Split split = service("5", "0", true).chargeOnCompletion(request);

        assertThat(split.commission()).isEqualByComparingTo("2360");
        ArgumentCaptor<ProviderWalletTransaction> debit = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(ledger).save(debit.capture());
        assertThat(debit.getValue().getAmount()).isEqualByComparingTo("-2360");
        assertThat(debit.getValue().getWallet()).isSameAs(wallet);

        ArgumentCaptor<PlatformWalletTransaction> platform = ArgumentCaptor.forClass(PlatformWalletTransaction.class);
        verify(platformRepository).save(platform.capture());
        assertThat(platform.getValue().getAmount()).isEqualByComparingTo("2360");
        assertThat(platform.getValue().getGrossAmount()).isEqualByComparingTo("47200");
        assertThat(platform.getValue().getSource()).isEqualTo(CommissionSource.FIN_DE_TRAVAUX);
        assertThat(platform.getValue().getSyndic().getId()).isEqualTo(65L);
    }

    @Test
    void completion_isChargedOnlyOncePerIntervention() {
        when(platformRepository.existsByInterventionRequestIdAndCategory(51L, PlatformWalletTransactionCategory.COMMISSION_TRAVAUX))
                .thenReturn(true);

        service("5", "0", true).chargeOnCompletion(request);

        verify(ledger, never()).save(any());
        verify(platformRepository, never()).save(any());
    }

    @Test
    void completion_withoutRateOrDisabled_debitsNothing() {
        service("0", "0", true).chargeOnCompletion(request);
        service("5", "0", false).chargeOnCompletion(request);

        verify(ledger, never()).save(any());
        verify(platformRepository, never()).save(any());
    }

    @Test
    void quote_isRefusedWhenTheWalletDoesNotCoverItsCommission() {
        balance("2000");
        TravauxCommissionService service = service("5", "0", true);

        assertThatThrownBy(() -> service.assertProviderCanCover(provider, request, new BigDecimal("47200")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2360 FCFA")
                .hasMessageContaining("360 FCFA");
        assertThatCode(() -> service.assertProviderCanCover(provider, request, new BigDecimal("40000")))
                .doesNotThrowAnyException(); // commission 2000 = solde
    }

    @Test
    void usesThePlatformDefaultWhenTheSyndicHasNoRate_andRoundsToTheFranc() {
        TravauxCommissionService.Split split = service(null, "2.5", true).split(request, new BigDecimal("1001"));

        assertThat(split.rate()).isEqualByComparingTo("2.5");
        assertThat(split.commission()).isEqualByComparingTo("25");
    }

    @Test
    void paymentsThroughSolimusAreCreditedInFull() {
        service("5", "0", true).creditProvider(wallet, request, new BigDecimal("10000"),
                ProviderWalletTransactionCategory.TRAVAUX, "Acompte — Fuite hall", null);

        ArgumentCaptor<ProviderWalletTransaction> credit = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(ledger).save(credit.capture());
        assertThat(credit.getValue().getAmount()).isEqualByComparingTo("10000");
        verify(platformRepository, never()).save(any());
        verify(walletRepository, never()).findByProviderIdForUpdate(anyLong());
    }
}
