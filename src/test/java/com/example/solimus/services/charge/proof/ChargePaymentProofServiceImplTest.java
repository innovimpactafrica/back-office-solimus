package com.example.solimus.services.charge.proof;

import com.example.solimus.dtos.payment.proof.ChargePaymentProofDTO;
import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ConflictException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.shared.PaymentProofDocumentService;
import com.example.solimus.services.shared.StatusRecalculationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargePaymentProofServiceImplTest {

    @Mock private ChargePaymentProofRepository proofRepository;
    @Mock private ChargeCallItemRepository chargeCallItemRepository;
    @Mock private ExceptionalCallItemRepository exceptionalCallItemRepository;
    @Mock private ChargeCallPaymentRepository chargeCallPaymentRepository;
    @Mock private ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private StatusRecalculationService statusRecalculationService;
    @Mock private NotificationService notificationService;
    @Mock private PaymentProofDocumentService documentService;
    @Mock private SyndicWalletTransactionRepository syndicWalletTransactionRepository; // ne doit jamais servir
    @InjectMocks private ChargePaymentProofServiceImpl service;

    private User owner;
    private User syndic;
    private ChargeCallItem item;

    @BeforeEach
    void setUp() {
        owner = user(10L, "owner@test.sn");
        syndic = user(20L, "syndic@test.sn");
        Residence residence = new Residence();
        residence.setId(1L);
        residence.setName("Résidence Test");
        residence.setSyndic(syndic);
        Budget budget = new Budget();
        budget.setResidence(residence);
        budget.setStatus(BudgetStatus.ACTIVE);
        ChargeCall call = new ChargeCall();
        call.setBudget(budget);
        call.setFrequency(ChargeFrequency.TRIMESTRIEL);
        call.setPeriodNumber(3);
        call.setYear(2026);
        item = new ChargeCallItem();
        item.setId(100L);
        item.setReference("ACI-1");
        item.setChargeCall(call);
        item.setCoOwner(owner);
        item.setQuotePart(new BigDecimal("45000"));
        item.setPaidAmount(BigDecimal.ZERO);
        item.setStatus(ChargeItemPaymentStatus.PENDING);

        when(chargeCallItemRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(item));
        when(chargeCallPaymentRepository.findFirstByChargeCallItemIdOrderByPaidAtDesc(100L)).thenReturn(Optional.empty());
        when(documentService.store(any())).thenReturn(new PaymentProofDocumentService.StoredDocument("k.pdf", "recu.pdf", "application/pdf"));
        when(proofRepository.save(any())).thenAnswer(inv -> {
            ChargePaymentProof p = inv.getArgument(0);
            if (p.getId() == null) p.setId(7L);
            return p;
        });
        when(chargeCallPaymentRepository.save(any())).thenAnswer(inv -> {
            ChargeCallPayment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(55L);
            return p;
        });
        when(propertyRepository.findByOwnerIdAndResidenceId(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submittingAProofPutsTheChargeInPendingValidationWithoutPayingIt() {
        login(owner);
        ChargePaymentProofDTO dto = service.submitProof(ChargeType.REGULAR, 100L, "Virement du 10/11",
                OffPlatformPaymentMethod.VIREMENT, null, pdf());

        assertThat(dto.getStatus()).isEqualTo(ProofStatus.PENDING_VALIDATION);
        assertThat(dto.getReference()).isEqualTo("PRV-000007");
        assertThat(dto.getAmount()).isEqualByComparingTo("45000");
        assertThat(item.getProofStatus()).isEqualTo(ProofStatus.PENDING_VALIDATION);
        assertThat(item.getStatus()).isEqualTo(ChargeItemPaymentStatus.PENDING); // pas encore payée
        verify(chargeCallPaymentRepository, never()).save(any());
    }

    @Test
    void aSecondProofWhileOneIsPendingIsAConflict() {
        login(owner);
        when(proofRepository.existsByChargeCallItemIdAndStatus(100L, ProofStatus.PENDING_VALIDATION)).thenReturn(true);

        assertThatThrownBy(() -> service.submitProof(ChargeType.REGULAR, 100L, "Virement", null, null, pdf()))
                .isInstanceOf(ConflictException.class);
        verify(documentService, never()).store(any());
    }

    @Test
    void anotherCoOwnerCannotSubmitAProof() {
        login(user(11L, "autre@test.sn"));
        assertThatThrownBy(() -> service.submitProof(ChargeType.REGULAR, 100L, "Virement", null, null, pdf()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aPaidChargeRefusesAProof() {
        login(owner);
        item.setStatus(ChargeItemPaymentStatus.PAID);
        item.setPaidAmount(new BigDecimal("45000"));
        assertThatThrownBy(() -> service.submitProof(ChargeType.REGULAR, 100L, "Virement", null, null, pdf()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("déjà payée");
    }

    @Test
    void validationPaysTheChargeOffPlatformWithoutCreditingTheSyndicWallet() {
        ChargePaymentProof proof = pendingProof();
        login(syndic);
        when(proofRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(proof));

        ProofBatchResultDTO result = service.validateProofs(List.of(7L));

        assertThat(result.getSucceededIds()).containsExactly(7L);
        assertThat(item.getStatus()).isEqualTo(ChargeItemPaymentStatus.PAID);
        assertThat(item.getPaidAmount()).isEqualByComparingTo("45000");
        assertThat(item.getProofStatus()).isEqualTo(ProofStatus.VALIDATED);
        ArgumentCaptor<ChargeCallPayment> payment = ArgumentCaptor.forClass(ChargeCallPayment.class);
        verify(chargeCallPaymentRepository, atLeastOnce()).save(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getValue().getOffPlatform()).isTrue();
        assertThat(proof.getPaymentReference()).isEqualTo("CPY-000055");
        verifyNoInteractions(syndicWalletTransactionRepository);
    }

    @Test
    void aSyndicOfAnotherResidenceCannotValidate() {
        ChargePaymentProof proof = pendingProof();
        login(user(21L, "autre-syndic@test.sn"));
        when(proofRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(proof));

        ProofBatchResultDTO result = service.validateProofs(List.of(7L, 8L));

        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getFailures()).hasSize(2);
        assertThat(item.getStatus()).isEqualTo(ChargeItemPaymentStatus.PENDING);
    }

    @Test
    void rejectionPutsTheChargeBackToPay() {
        ChargePaymentProof proof = pendingProof();
        login(syndic);
        when(proofRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(proof));

        ProofBatchResultDTO result = service.rejectProofs(List.of(7L), "Montant incorrect");

        assertThat(result.getSucceededIds()).containsExactly(7L);
        assertThat(proof.getStatus()).isEqualTo(ProofStatus.REJECTED);
        assertThat(proof.getRejectionReason()).isEqualTo("Montant incorrect");
        assertThat(item.getProofStatus()).isEqualTo(ProofStatus.REJECTED);
        assertThat(ChargeDisplayStatus.of(item.getStatus(), item.getProofStatus())).isEqualTo(ChargeDisplayStatus.PENDING);
    }

    // ── utilitaires ──

    private ChargePaymentProof pendingProof() {
        item.setProofStatus(ProofStatus.PENDING_VALIDATION);
        ChargePaymentProof proof = new ChargePaymentProof();
        proof.setId(7L);
        proof.setReference("PRV-000007");
        proof.setChargeType(ChargeType.REGULAR);
        proof.setChargeCallItem(item);
        proof.setCoOwner(owner);
        proof.setResidence(item.getChargeCall().getBudget().getResidence());
        proof.setAmount(new BigDecimal("45000"));
        proof.setStatus(ProofStatus.PENDING_VALIDATION);
        proof.setDocumentKey("k.pdf");
        return proof;
    }

    private void login(User user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user.getEmail(), null));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private static User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFirstName("Prénom" + id);
        u.setLastName("Nom");
        return u;
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("document", "recu.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
    }
}
