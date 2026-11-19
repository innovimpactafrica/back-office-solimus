package com.example.solimus.services.travaux;

import com.example.solimus.dtos.payment.proof.InterventionPaymentProofDTO;
import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.dtos.payment.proof.ProviderPaymentProofPageDTO;
import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofPayerType;
import com.example.solimus.enums.ProofStatus;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Paiement des travaux HORS plateforme : le payeur (syndic ou copropriétaire) déclare l'acompte ou le
 * solde avec une preuve ; le prestataire, qui reçoit les fonds, confirme la réception ou la rejette.
 */
public interface InterventionPaymentProofService {

    // ── Payeur (syndic / copropriétaire) ──
    InterventionPaymentProofDTO declareDeposit(ProofPayerType payerType, Long interventionId, BigDecimal amount,
                                               String description, OffPlatformPaymentMethod paymentMethod,
                                               LocalDate paymentDate, MultipartFile document);

    InterventionPaymentProofDTO declareBalance(ProofPayerType payerType, Long interventionId, String description,
                                               OffPlatformPaymentMethod paymentMethod, LocalDate paymentDate,
                                               MultipartFile document);

    List<InterventionPaymentProofDTO> getProofsForPayer(ProofPayerType payerType, Long interventionId);

    // ── Prestataire ──
    ProviderPaymentProofPageDTO getProofsForProvider(ProofStatus status, String search, int page, int size);

    InterventionPaymentProofDTO getProofForProvider(Long proofId);

    ProofBatchResultDTO confirmProofs(List<Long> proofIds);

    ProofBatchResultDTO rejectProofs(List<Long> proofIds, String reason);
}
