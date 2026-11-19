package com.example.solimus.services.travaux;

import com.example.solimus.dtos.payment.proof.InterventionPaymentProofDTO;
import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterventionPaymentProofServiceImplTest {

    @Mock private InterventionPaymentProofRepository proofRepository;
    @Mock private InterventionRequestRepository interventionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private StatusRecalculationService statusRecalculationService;
    @Mock private NotificationService notificationService;
    @Mock private PaymentProofDocumentService documentService;
    @InjectMocks private InterventionPaymentProofServiceImpl service;

    private User owner;
    private User provider;
    private InterventionRequest request;

    @BeforeEach
    void setUp() {
        owner = user(10L, "owner@test.sn");
        provider = user(30L, "provider@test.sn");
        User syndic = user(20L, "syndic@test.sn");
        Residence residence = new Residence();
        residence.setName("Résidence Test");
        residence.setSyndic(syndic);
        request = new InterventionRequest();
        request.setId(5L);
        request.setTitle("Fuite d'eau");
        request.setResidence(residence);
        request.setOwner(owner);
        request.setQuoteAcceptedBy(owner);
        request.setManagementMode(InterventionManagementMode.OWNER);
        request.setSelectedProvider(provider);
        request.setStatus(InterventionStatus.QUOTE_VALIDATED);
        request.setTotalAmount(new BigDecimal("100000"));
        request.setDepositAmount(BigDecimal.ZERO);
        request.setRemainingAmount(new BigDecimal("100000"));
        request.setHistory(new ArrayList<>());

        when(interventionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(paymentRepository.findByInterventionRequestIdAndType(any(), any())).thenReturn(Optional.empty());
        when(documentService.store(any())).thenReturn(new PaymentProofDocumentService.StoredDocument("k.png", "recu.png", "image/png"));
        when(proofRepository.save(any())).thenAnswer(inv -> {
            InterventionPaymentProof p = inv.getArgument(0);
            if (p.getId() == null) p.setId(9L);
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            PaymentProvider p = inv.getArgument(0);
            if (p.getId() == null) p.setId(77L);
            return p;
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theOwnerWhoAcceptedTheQuoteDeclaresTheDeposit() {
        login(owner);
        InterventionPaymentProofDTO dto = service.declareDeposit(ProofPayerType.COPROPRIETAIRE, 5L, new BigDecimal("30000"),
                "Espèces remises au prestataire", OffPlatformPaymentMethod.ESPECES, null, png());

        assertThat(dto.getStatus()).isEqualTo(ProofStatus.PENDING_VALIDATION);
        assertThat(dto.getType()).isEqualTo(PaymentType.ACOMPTE);
        assertThat(dto.getReference()).isEqualTo("PTV-000009");
        assertThat(request.getDepositAmount()).isEqualByComparingTo("0"); // rien n'est enregistré avant confirmation
    }

    @Test
    void theSyndicCannotDeclareWhenTheOwnerAcceptedTheQuote() {
        login(request.getResidence().getSyndic());
        assertThatThrownBy(() -> service.declareDeposit(ProofPayerType.SYNDIC, 5L, new BigDecimal("30000"),
                "Virement", null, null, png())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aDepositAboveTheQuoteIsRefused() {
        login(owner);
        assertThatThrownBy(() -> service.declareDeposit(ProofPayerType.COPROPRIETAIRE, 5L, new BigDecimal("100001"),
                "Virement", null, null, png())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void theBalanceRequiresFinishedWorks() {
        login(owner);
        assertThatThrownBy(() -> service.declareBalance(ProofPayerType.COPROPRIETAIRE, 5L, "Virement", null, null, png()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("terminés");
    }

    @Test
    void confirmingTheBalanceClosesTheInterventionWithoutTouchingWallets() {
        request.setStatus(InterventionStatus.FINISHED);
        request.setDepositAmount(new BigDecimal("30000"));
        request.setRemainingAmount(new BigDecimal("70000"));
        InterventionPaymentProof proof = proof(PaymentType.SOLDE, new BigDecimal("70000"));
        login(provider);
        when(proofRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(proof));

        ProofBatchResultDTO result = service.confirmProofs(List.of(9L));

        assertThat(result.getSucceededIds()).containsExactly(9L);
        assertThat(request.getStatus()).isEqualTo(InterventionStatus.FINAL_VALIDATION);
        assertThat(request.getDepositAmount()).isEqualByComparingTo("100000");
        assertThat(proof.getStatus()).isEqualTo(ProofStatus.VALIDATED);
        ArgumentCaptor<PaymentProvider> payment = ArgumentCaptor.forClass(PaymentProvider.class);
        verify(paymentRepository, atLeastOnce()).save(payment.capture());
        assertThat(payment.getValue().getOffPlatform()).isTrue();
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(proof.getPaymentReference()).isEqualTo("SOL-000077");
    }

    @Test
    void anotherProviderCannotConfirm() {
        InterventionPaymentProof proof = proof(PaymentType.ACOMPTE, new BigDecimal("30000"));
        login(user(31L, "autre@test.sn"));
        when(proofRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(proof));

        ProofBatchResultDTO result = service.confirmProofs(List.of(9L));

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(request.getDepositAmount()).isEqualByComparingTo("0");
    }

    // ── utilitaires ──

    private InterventionPaymentProof proof(PaymentType type, BigDecimal amount) {
        InterventionPaymentProof proof = new InterventionPaymentProof();
        proof.setId(9L);
        proof.setReference("PTV-000009");
        proof.setInterventionRequest(request);
        proof.setType(type);
        proof.setAmount(amount);
        proof.setPayer(owner);
        proof.setPayerType(ProofPayerType.COPROPRIETAIRE);
        proof.setProvider(provider);
        proof.setStatus(ProofStatus.PENDING_VALIDATION);
        proof.setDocumentKey("k.png");
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

    private static MockMultipartFile png() {
        return new MockMultipartFile("document", "recu.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2});
    }
}
