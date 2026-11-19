package com.example.solimus.services.charge.proof;

import com.example.solimus.dtos.payment.proof.ChargePaymentProofDTO;
import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.dtos.payment.proof.SyndicChargeProofPageDTO;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Paiement des charges HORS plateforme : le copropriétaire dépose une preuve, le syndic la valide
 * (la charge passe à « Payé ») ou la rejette (la charge redevient « En attente »).
 */
public interface ChargePaymentProofService {

    // ── Copropriétaire ──
    ChargePaymentProofDTO submitProof(ChargeType type, Long chargeId, String description,
                                      OffPlatformPaymentMethod paymentMethod, LocalDate paymentDate,
                                      MultipartFile document);

    List<ChargePaymentProofDTO> getMyProofs(ChargeType type, Long chargeId);

    // ── Syndic ──
    SyndicChargeProofPageDTO getProofsForSyndic(ProofStatus status, Long residenceId, ChargeType chargeType,
                                                String search, int page, int size);

    ChargePaymentProofDTO getProofForSyndic(Long proofId);

    ProofBatchResultDTO validateProofs(List<Long> proofIds);

    ProofBatchResultDTO rejectProofs(List<Long> proofIds, String reason);
}
