package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.syndic.travaux.PayDepositDTO;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.Quote;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.PaymentType;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.payment.PaymentConfirmationService;
import com.example.solimus.services.shared.StatusRecalculationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Copropriétaire : accepte le devis de SA demande (même partie commune), paie acompte / solde,
// et le wallet du prestataire est crédité à la confirmation du paiement
class OwnerTravauxPaymentFlowTest {

    private ownerTravauxServiceImpl service;
    private InterventionRequestRepository interventionRepo;
    private QuoteRepository quoteRepo;
    private User owner;
    private User provider;
    private User syndic;
    private InterventionRequest request;
    private Quote quote;

    @BeforeEach
    void setUp() {
        owner = user(69L);
        owner.setEmail("copro@example.test");
        provider = user(9L);
        syndic = user(65L);
        Residence residence = new Residence();
        residence.setId(1L);
        residence.setSyndic(syndic);

        // Demande créée par le copropriétaire via travaux-manual : mode SYNDIC, partie commune
        request = new InterventionRequest();
        request.setId(51L);
        request.setTitle("Fuite hall");
        request.setOwner(owner);
        request.setResidence(residence);
        request.setManagementMode(InterventionManagementMode.SYNDIC);
        request.setLocationType(IncidentLocationType.PARTIE_COMMUNE);

        quote = new Quote();
        quote.setId(7L);
        quote.setProvider(provider);
        quote.setInterventionRequest(request);
        quote.setStatus(QuoteStatus.SENT);
        quote.setTotalAmount(new BigDecimal("47200.00"));

        interventionRepo = mock(InterventionRequestRepository.class);
        quoteRepo = mock(QuoteRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findByEmail("copro@example.test")).thenReturn(Optional.of(owner));
        when(interventionRepo.findByIdForUpdate(51L)).thenReturn(Optional.of(request));
        when(quoteRepo.findById(7L)).thenReturn(Optional.of(quote));
        when(quoteRepo.findAllByInterventionRequestOrderByTotalAmountAsc(request)).thenReturn(List.of(quote));
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        when(paymentRepo.findByInterventionRequestIdAndType(anyLong(), any())).thenReturn(Optional.empty());
        when(paymentRepo.save(any())).thenAnswer(inv -> {
            PaymentProvider p = inv.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });

        service = mock(ownerTravauxServiceImpl.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "interventionRequestRepository", interventionRepo);
        ReflectionTestUtils.setField(service, "quoteRepository", quoteRepo);
        ReflectionTestUtils.setField(service, "userRepository", userRepo);
        ReflectionTestUtils.setField(service, "paymentRepository", paymentRepo);
        ReflectionTestUtils.setField(service, "notificationService", mock(NotificationService.class));
        ReflectionTestUtils.setField(service, "touchPayBridgeUrlTemplate", "https://bridge/%s");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("copro@example.test", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("U" + id);
        u.setLastName("X");
        return u;
    }

    @Test
    void ownerAcceptsQuoteOfHisOwnRequest_evenCommonPart_andBecomesPayer() {
        service.acceptQuote(51L, 7L); // avant : 403 "Les devis de cette intervention sont gérés par le syndic."

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(request.getSelectedProvider()).isSameAs(provider);
        assertThat(request.getQuoteAcceptedBy()).isSameAs(owner);
        assertThat(request.getTotalAmount()).isEqualByComparingTo("47200");

        // Il peut ensuite initier l'acompte (Wave / OM ou TouchPay)
        assertThat(service.payDeposit(51L, PayDepositDTO.builder()
                .montant(new BigDecimal("10000")).methode(PaymentMethod.WAVE).build()).getTransactionReference())
                .startsWith("PAY-");
    }

    @Test
    void ownerCannotAcceptQuoteOnSomeoneElsesRequest() {
        request.setOwner(user(999L));
        assertThatThrownBy(() -> service.acceptQuote(51L, 7L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownerCannotPay_whenSyndicValidatedTheQuote() {
        request.setSelectedProvider(provider);
        request.setTotalAmount(new BigDecimal("47200"));
        request.setQuoteAcceptedBy(syndic);
        assertThatThrownBy(() -> service.payDeposit(51L, PayDepositDTO.builder()
                .montant(new BigDecimal("10000")).methode(PaymentMethod.WAVE).build()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("syndic");
    }

    @Test
    void confirmedDeposit_creditsProviderWallet_linkedToIntervention() {
        request.setSelectedProvider(provider);
        request.setTotalAmount(new BigDecimal("47200"));
        request.setStatus(InterventionStatus.QUOTE_VALIDATED);

        PaymentProvider payment = PaymentProvider.builder()
                .reference("PAY-000001").interventionRequest(request).provider(provider).paymentInitiator(owner)
                .amount(new BigDecimal("10000")).type(PaymentType.ACOMPTE).status(PaymentStatus.PENDING).build();

        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        when(paymentRepo.findByReferenceForUpdate("PAY-000001")).thenReturn(Optional.of(payment));
        ProviderWalletRepository walletRepo = mock(ProviderWalletRepository.class);
        when(walletRepo.findByProviderIdForUpdate(9L)).thenReturn(Optional.empty()); // créé à la volée
        when(walletRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ProviderWalletTransactionRepository ledger = mock(ProviderWalletTransactionRepository.class);

        PaymentConfirmationService confirmation = mock(PaymentConfirmationService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(confirmation, "paymentRepository", paymentRepo);
        ReflectionTestUtils.setField(confirmation, "interventionRepository", interventionRepo);
        ReflectionTestUtils.setField(confirmation, "providerWalletRepository", walletRepo);
        ReflectionTestUtils.setField(confirmation, "providerWalletTransactionRepository", ledger);
        ReflectionTestUtils.setField(confirmation, "notificationService", mock(NotificationService.class));
        ReflectionTestUtils.setField(confirmation, "activityLogRepository", mock(ActivityLogRepository.class));
        ReflectionTestUtils.setField(confirmation, "statusRecalculationService", mock(StatusRecalculationService.class));

        confirmation.routeCallbackByPrefix("PAY-000001", true);

        ArgumentCaptor<ProviderWalletTransaction> credit = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(ledger).save(credit.capture());
        assertThat(credit.getValue().getAmount()).isEqualByComparingTo("10000");
        assertThat(credit.getValue().getCategory()).isEqualTo(ProviderWalletTransactionCategory.INTERVENTION_PAYMENT);
        assertThat(credit.getValue().getInterventionRequest()).isSameAs(request);
        assertThat(credit.getValue().getLabel()).isEqualTo("Acompte — Fuite hall");
        assertThat(((ProviderWallet) credit.getValue().getWallet()).getProvider()).isSameAs(provider);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(request.getDepositAmount()).isEqualByComparingTo("10000");

        // Callback / réconciliation rejoués : aucun second crédit
        confirmation.routeCallbackByPrefix("PAY-000001", true);
        verify(ledger, org.mockito.Mockito.times(1)).save(any());
        verify(walletRepo, never()).findByProviderId(anyLong());
    }
}
