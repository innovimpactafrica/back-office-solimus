package com.example.solimus.services.provider.wallet;

import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.dtos.provider.wallet.recharge.AdminDirectRechargeDTO;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletRecharge;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.Role;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofStatus;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.enums.RechargeOrigin;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.ProviderWalletRechargeRepository;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.provider.eligibility.ProviderEligibilityService;
import com.example.solimus.services.shared.PaymentProofDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Recharge du wallet prestataire : preuve → validation admin → crédit RECHARGE ; rejet ; crédit direct. */
class ProviderWalletRechargeServiceTest {

    private final ProviderWalletRechargeRepository rechargeRepository = mock(ProviderWalletRechargeRepository.class);
    private final ProviderWalletRepository walletRepository = mock(ProviderWalletRepository.class);
    private final ProviderWalletTransactionRepository ledger = mock(ProviderWalletTransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PaymentProofDocumentService documentService = mock(PaymentProofDocumentService.class);
    private ProviderWalletRechargeService service;
    private User provider;
    private User admin;

    @BeforeEach
    void setUp() {
        provider = user(9L, "presta@example.test", ERole.ROLE_PRESTATAIRE);
        admin = user(1L, "admin@example.test", ERole.ROLE_ADMIN);
        when(userRepository.findByEmail("presta@example.test")).thenReturn(Optional.of(provider));
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.of(admin));
        when(userRepository.findById(9L)).thenReturn(Optional.of(provider));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(rechargeRepository.save(any())).thenAnswer(inv -> {
            ProviderWalletRecharge r = inv.getArgument(0);
            if (r.getId() == null) r.setId(7L);
            return r;
        });
        when(walletRepository.findByProviderIdForUpdate(9L)).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.save(any())).thenAnswer(inv -> {
            ProviderWalletTransaction t = inv.getArgument(0);
            t.setId(55L);
            return t;
        });
        when(documentService.store(any())).thenReturn(
                new PaymentProofDocumentService.StoredDocument("payment-proofs/x.pdf", "recu.pdf", "application/pdf"));
        service = new ProviderWalletRechargeService(rechargeRepository, walletRepository, ledger, userRepository,
                documentService, mock(NotificationService.class), mock(ProviderEligibilityService.class),
                mock(WalletBalanceService.class), new BigDecimal("500"), new BigDecimal("5000000"), 3, "Wave Solimus");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static User user(long id, String email, ERole roleName) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFirstName("U" + id);
        u.setLastName("X");
        Role role = new Role();
        role.setName(roleName);
        u.setRole(role);
        return u;
    }

    private static void loginAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private ProviderWalletRecharge pending() {
        return ProviderWalletRecharge.builder().id(7L).reference("RCH-000007").provider(provider)
                .amount(new BigDecimal("5000")).paymentMethod(OffPlatformPaymentMethod.WAVE)
                .status(ProofStatus.PENDING_VALIDATION).build();
    }

    @Test
    void providerSubmitsAProof_itWaitsForValidation_andNothingIsCredited() {
        loginAs("presta@example.test");
        var dto = service.submit(new BigDecimal("5000"), OffPlatformPaymentMethod.WAVE, LocalDate.now(),
                "Wave du jour", new MockMultipartFile("document", "recu.pdf", "application/pdf", new byte[]{1}));

        assertThat(dto.getReference()).isEqualTo("RCH-000007");
        assertThat(dto.getStatus()).isEqualTo(ProofStatus.PENDING_VALIDATION);
        assertThat(dto.getOrigin()).isEqualTo(RechargeOrigin.PREUVE_PRESTATAIRE);
        verify(ledger, never()).save(any());
    }

    @Test
    void refusesAmountsOutsideTheLimits_andTooManyPendingRecharges() {
        loginAs("presta@example.test");
        var file = new MockMultipartFile("document", "recu.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> service.submit(new BigDecimal("100"), null, null, "Wave", file))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("minimum");
        assertThatThrownBy(() -> service.submit(new BigDecimal("1000.50"), null, null, "Wave", file))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("entier");

        when(rechargeRepository.countByProviderIdAndStatus(9L, ProofStatus.PENDING_VALIDATION)).thenReturn(3L);
        assertThatThrownBy(() -> service.submit(new BigDecimal("5000"), null, null, "Wave", file))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("en attente");
    }

    @Test
    void adminValidation_creditsTheWalletOnce_withARechargeLine() {
        loginAs("admin@example.test");
        ProviderWalletRecharge recharge = pending();
        when(rechargeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(recharge));

        ProofBatchResultDTO first = service.validate(List.of(7L));
        ProofBatchResultDTO second = service.validate(List.of(7L));

        ArgumentCaptor<ProviderWalletTransaction> line = ArgumentCaptor.forClass(ProviderWalletTransaction.class);
        verify(ledger).save(line.capture()); // une seule fois
        assertThat(line.getValue().getCategory()).isEqualTo(ProviderWalletTransactionCategory.RECHARGE);
        assertThat(line.getValue().getAmount()).isEqualByComparingTo("5000");
        assertThat(line.getValue().getReference()).isEqualTo("RCH-000007");
        assertThat(((ProviderWallet) line.getValue().getWallet()).getProvider()).isSameAs(provider);
        assertThat(recharge.getStatus()).isEqualTo(ProofStatus.VALIDATED);
        assertThat(recharge.getReviewedBy()).isSameAs(admin);
        assertThat(first.getSucceeded()).isEqualTo(1);
        assertThat(second.getFailed()).isEqualTo(1);
    }

    @Test
    void adminRejection_creditsNothing() {
        loginAs("admin@example.test");
        ProviderWalletRecharge recharge = pending();
        when(rechargeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(recharge));

        service.reject(List.of(7L), "Montant non reçu");

        assertThat(recharge.getStatus()).isEqualTo(ProofStatus.REJECTED);
        assertThat(recharge.getRejectionReason()).isEqualTo("Montant non reçu");
        verify(ledger, never()).save(any());
    }

    @Test
    void directCredit_isImmediate_andOnlyForProviders() {
        loginAs("admin@example.test");
        AdminDirectRechargeDTO dto = new AdminDirectRechargeDTO();
        dto.setProviderId(9L);
        dto.setAmount(new BigDecimal("2000"));
        dto.setPaymentMethod(OffPlatformPaymentMethod.ESPECES);
        dto.setDescription("Espèces au bureau");

        var result = service.creditDirectly(dto);
        assertThat(result.getStatus()).isEqualTo(ProofStatus.VALIDATED);
        assertThat(result.getOrigin()).isEqualTo(RechargeOrigin.CREDIT_ADMIN);
        verify(ledger).save(any());

        dto.setProviderId(1L);
        assertThatThrownBy(() -> service.creditDirectly(dto))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("pas un prestataire");
        verify(walletRepository, never()).findByProviderIdForUpdate(1L);
        verify(walletRepository).findByProviderIdForUpdate(anyLong());
    }
}
