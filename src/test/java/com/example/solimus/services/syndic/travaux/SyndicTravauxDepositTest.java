package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.travaux.SyndicPayDepositDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicPaymentResultDTO;
import com.example.solimus.entities.BudgetItem;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.SyndicWallet;
import com.example.solimus.entities.SyndicWalletTransaction;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import com.example.solimus.repositories.SyndicWalletRepository;
import com.example.solimus.repositories.SyndicWalletTransactionRepository;
import com.example.solimus.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TRA-06 : l'acompte versé par le syndic débite son wallet ET crédite le wallet du prestataire
@ExtendWith(MockitoExtension.class)
class SyndicTravauxDepositTest {

    @Mock private UserRepository userRepository;
    @Mock private InterventionRequestRepository interventionRepository;
    @Mock private SyndicWalletRepository syndicWalletRepository;
    @Mock private SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    @Mock private ProviderWalletRepository providerWalletRepository;
    @Mock private ProviderWalletTransactionRepository providerWalletTransactionRepository;
    @Mock private com.example.solimus.repositories.ActivityLogRepository activityLogRepository;

    @InjectMocks private SyndicTravauxServiceImpl service;

    private User syndic;
    private User provider;
    private InterventionRequest request;

    @BeforeEach
    void setUp() {
        syndic = new User();
        syndic.setId(65L);
        syndic.setEmail("syndic@example.test");
        provider = new User();
        provider.setId(9L);
        provider.setFirstName("Pres");
        provider.setLastName("Tataire");

        Residence residence = new Residence();
        residence.setId(1L);
        residence.setSyndic(syndic);

        request = new InterventionRequest();
        request.setId(48L);
        request.setTitle("Fuite");
        request.setResidence(residence);
        request.setSelectedProvider(provider);
        request.setTotalAmount(new BigDecimal("1180.00"));
        request.setDepositAmount(BigDecimal.ZERO);
        request.setBudgetItem(new BudgetItem()); // poste déjà choisi

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("syndic@example.test", null, List.of()));
        when(userRepository.findByEmail("syndic@example.test")).thenReturn(Optional.of(syndic));
        when(interventionRepository.findByIdForUpdate(48L)).thenReturn(Optional.of(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void walletWithBalance(String balance) {
        SyndicWallet wallet = SyndicWallet.builder().id(5L).syndic(syndic).build();
        when(syndicWalletRepository.findBySyndicId(65L)).thenReturn(Optional.of(wallet));
        when(syndicWalletTransactionRepository.sumTransactionsUpTo(any(), any())).thenReturn(new BigDecimal(balance));
    }

    @Test
    void deposit_debitsSyndic_andCreditsProvider_evenWithoutExistingProviderWallet() {
        walletWithBalance("5000");
        // Le prestataire n'a jamais ouvert son wallet : il est créé à la volée
        when(providerWalletRepository.findByProviderIdForUpdate(9L)).thenReturn(Optional.empty());
        when(providerWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyndicPaymentResultDTO result = service.payDeposit(48L,
                SyndicPayDepositDTO.builder().montant(new BigDecimal("500")).build());

        ArgumentCaptor<SyndicWalletTransaction> debit = ArgumentCaptor.forClass(SyndicWalletTransaction.class);
        verify(syndicWalletTransactionRepository).save(debit.capture());
        assertThat(debit.getValue().getAmount()).isEqualByComparingTo("-500");
        assertThat(debit.getValue().getCategory()).isEqualTo(WalletTransactionCategory.BUDGET_EXPENSE);

        ArgumentCaptor<ProviderWalletTransaction> credit = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(providerWalletTransactionRepository).save(credit.capture());
        assertThat(credit.getValue().getAmount()).isEqualByComparingTo("500");
        assertThat(credit.getValue().getCategory()).isEqualTo(ProviderWalletTransactionCategory.TRAVAUX);
        assertThat(credit.getValue().getWallet().getProvider()).isSameAs(provider);
        assertThat(credit.getValue().getInterventionRequest()).isSameAs(request);

        assertThat(result.getNouveauSoldeWallet()).isEqualByComparingTo("4500");
        assertThat(request.getDepositAmount()).isEqualByComparingTo("500");
    }

    @Test
    void deposit_insufficientSyndicBalance_creditsNobody() {
        walletWithBalance("100");

        assertThatThrownBy(() -> service.payDeposit(48L,
                SyndicPayDepositDTO.builder().montant(new BigDecimal("500")).build()))
                .isInstanceOf(BadRequestException.class);
        verify(syndicWalletTransactionRepository, never()).save(any());
        verify(providerWalletTransactionRepository, never()).save(any());
    }

    @Test
    void deposit_aboveTtc_isRejected() {
        assertThatThrownBy(() -> service.payDeposit(48L,
                SyndicPayDepositDTO.builder().montant(new BigDecimal("2000")).build()))
                .isInstanceOf(BadRequestException.class);
        verify(providerWalletRepository, never()).findByProviderIdForUpdate(anyLong());
    }
}
