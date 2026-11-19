package com.example.solimus.services.charge.proof;

import com.example.solimus.dtos.payment.proof.ChargePaymentProofDTO;
import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.dtos.payment.proof.SyndicChargeProofPageDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ConflictException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.shared.PaymentProofDocumentService;
import com.example.solimus.services.shared.StatusRecalculationService;
import com.example.solimus.utils.AfterCommit;
import com.example.solimus.utils.ChargePeriodLabelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Règles métier du paiement des charges HORS plateforme.
 * <ul>
 *   <li>Une preuve solde TOUTE la charge (aucun paiement partiel, comme en ligne).</li>
 *   <li>Une seule preuve en attente par charge ; un paiement en ligne en cours bloque le dépôt.</li>
 *   <li>Budget clôturé / appel exceptionnel clôturé : aucune preuve acceptée (même règle qu'en ligne).</li>
 *   <li>Validation par le syndic de la résidence uniquement : création du paiement (référence CPY-/ECP-,
 *       marqué hors plateforme), charge PAYÉE, statuts du lot et de la résidence recalculés.</li>
 *   <li>Le wallet du syndic n'est PAS crédité : les fonds n'ont pas transité par Solimus, et ce wallet
 *       sert de plafond aux retraits versés par la plateforme. Les indicateurs financiers (encaissé,
 *       impayés) lisent les montants payés des charges et restent donc justes.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargePaymentProofServiceImpl implements ChargePaymentProofService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChargePaymentProofRepository proofRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final ExceptionalCallItemRepository exceptionalCallItemRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;
    private final PropertyRepository propertyRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final StatusRecalculationService statusRecalculationService;
    private final NotificationService notificationService;
    private final PaymentProofDocumentService documentService;

    // =========================================================================
    // COPROPRIÉTAIRE : DÉPÔT D'UNE PREUVE
    // =========================================================================

    @Override
    @Transactional
    public ChargePaymentProofDTO submitProof(ChargeType type, Long chargeId, String description,
                                             OffPlatformPaymentMethod paymentMethod, LocalDate paymentDate,
                                             MultipartFile document) {
        User owner = getCurrentUser();
        String cleanDescription = validateDescription(description);
        if (paymentDate != null && paymentDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("La date de paiement ne peut pas être dans le futur");
        }

        ChargePaymentProof proof = new ChargePaymentProof();
        proof.setChargeType(type);
        proof.setCoOwner(owner);
        proof.setDescription(cleanDescription);
        proof.setPaymentMethod(paymentMethod != null ? paymentMethod : OffPlatformPaymentMethod.AUTRE);
        proof.setPaymentDate(paymentDate);
        proof.setStatus(ProofStatus.PENDING_VALIDATION);

        String chargeTitle;
        Residence residence;
        if (type == ChargeType.REGULAR) {
            // Verrou : deux dépôts simultanés ne créent jamais deux preuves en attente
            ChargeCallItem item = chargeCallItemRepository.findByIdForUpdate(chargeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));
            assertOwner(item.getCoOwner(), owner);
            if (item.getChargeCall().getBudget().getStatus() == BudgetStatus.CLOSED) {
                throw new BadRequestException("Le budget de cette résidence a été clôturé, cette charge n'accepte plus de paiement");
            }
            assertPayable(item.getStatus(), item.getRemainingAmount());
            if (proofRepository.existsByChargeCallItemIdAndStatus(chargeId, ProofStatus.PENDING_VALIDATION)) {
                throw new ConflictException("Une preuve de paiement est déjà en attente de validation pour cette charge");
            }
            chargeCallPaymentRepository.findFirstByChargeCallItemIdOrderByPaidAtDesc(chargeId)
                    .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                    .ifPresent(p -> {
                        throw new BadRequestException("Un paiement en ligne est en cours pour cette charge : attendez son issue");
                    });
            residence = item.getChargeCall().getBudget().getResidence();
            proof.setChargeCallItem(item);
            proof.setAmount(item.getRemainingAmount());
            chargeTitle = ChargePeriodLabelUtil.chargeTitle(item.getChargeCall());
            item.setProofStatus(ProofStatus.PENDING_VALIDATION);
            item.setUpdatedAt(LocalDateTime.now());
            chargeCallItemRepository.save(item);

        } else if (type == ChargeType.EXCEPTIONAL) {
            ExceptionalCallItem item = exceptionalCallItemRepository.findByIdForUpdate(chargeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));
            assertOwner(item.getCoOwner(), owner);
            if (item.getExceptionalCall().getStatus() == ExceptionalCallStatus.CLOSED) {
                throw new BadRequestException("Cet appel exceptionnel est clôturé, il n'accepte plus de paiement");
            }
            BigDecimal remaining = remainingOf(item);
            assertPayable(item.getStatus(), remaining);
            if (proofRepository.existsByExceptionalCallItemIdAndStatus(chargeId, ProofStatus.PENDING_VALIDATION)) {
                throw new ConflictException("Une preuve de paiement est déjà en attente de validation pour cette charge");
            }
            exceptionalCallPaymentRepository.findFirstByExceptionalCallItemIdOrderByPaidAtDesc(chargeId)
                    .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                    .ifPresent(p -> {
                        throw new BadRequestException("Un paiement en ligne est en cours pour cette charge : attendez son issue");
                    });
            residence = item.getExceptionalCall().getResidence();
            proof.setExceptionalCallItem(item);
            proof.setAmount(remaining);
            chargeTitle = item.getExceptionalCall().getTitle();
            item.setProofStatus(ProofStatus.PENDING_VALIDATION);
            exceptionalCallItemRepository.save(item);

        } else {
            throw new ResourceNotFoundException("Type de charge invalide");
        }

        // Fichier déposé après tous les contrôles métier (pas de fichier orphelin pour un dépôt refusé)
        PaymentProofDocumentService.StoredDocument stored = documentService.store(document);
        proof.setDocumentKey(stored.key());
        proof.setDocumentName(stored.originalName());
        proof.setDocumentContentType(stored.contentType());
        proof.setResidence(residence);
        proof = proofRepository.save(proof);
        proof.setReference("PRV-" + String.format("%06d", proof.getId()));
        proof = proofRepository.save(proof);

        Long syndicId = residence.getSyndic().getId();
        String body = fullName(owner) + " a déclaré avoir payé " + proof.getAmount().toPlainString()
                + " FCFA hors plateforme — " + chargeTitle + ". Preuve à valider.";
        AfterCommit.run(() -> notificationService.sendNewPaymentNotification(syndicId, "Preuve de paiement à valider", body));

        log.info("Preuve {} déposée par le copropriétaire {} pour la charge {} {}", proof.getReference(),
                owner.getId(), type, chargeId);
        return toDto(proof);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChargePaymentProofDTO> getMyProofs(ChargeType type, Long chargeId) {
        User owner = getCurrentUser();
        List<ChargePaymentProof> proofs;
        if (type == ChargeType.REGULAR) {
            ChargeCallItem item = chargeCallItemRepository.findById(chargeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));
            assertOwner(item.getCoOwner(), owner);
            proofs = proofRepository.findByChargeCallItemIdOrderBySubmittedAtDesc(chargeId);
        } else if (type == ChargeType.EXCEPTIONAL) {
            ExceptionalCallItem item = exceptionalCallItemRepository.findById(chargeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));
            assertOwner(item.getCoOwner(), owner);
            proofs = proofRepository.findByExceptionalCallItemIdOrderBySubmittedAtDesc(chargeId);
        } else {
            throw new ResourceNotFoundException("Type de charge invalide");
        }
        return proofs.stream().map(this::toDto).toList();
    }

    // =========================================================================
    // SYNDIC : CONSULTATION
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicChargeProofPageDTO getProofsForSyndic(ProofStatus status, Long residenceId, ChargeType chargeType,
                                                       String search, int page, int size) {
        User syndic = getCurrentUser();
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        Page<ChargePaymentProofDTO> proofs = proofRepository
                .searchForSyndic(syndic.getId(), status, residenceId, chargeType, normalizedSearch, pageable)
                .map(this::toDto);
        return SyndicChargeProofPageDTO.builder()
                .pendingValidationCount(proofRepository.countBySyndicAndStatus(syndic.getId(), ProofStatus.PENDING_VALIDATION))
                .proofs(proofs)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChargePaymentProofDTO getProofForSyndic(Long proofId) {
        User syndic = getCurrentUser();
        ChargePaymentProof proof = proofRepository.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Preuve de paiement introuvable"));
        if (!isSyndicOf(proof, syndic)) {
            throw new ForbiddenException("Cette preuve ne concerne pas l'une de vos résidences");
        }
        return toDto(proof);
    }

    // =========================================================================
    // SYNDIC : VALIDATION / REJET (une ou plusieurs preuves)
    // =========================================================================

    @Override
    @Transactional
    public ProofBatchResultDTO validateProofs(List<Long> proofIds) {
        User syndic = getCurrentUser();
        List<Long> succeeded = new ArrayList<>();
        List<ProofBatchResultDTO.Failure> failures = new ArrayList<>();

        for (Long id : new LinkedHashSet<>(proofIds)) {
            ChargePaymentProof proof = proofRepository.findByIdForUpdate(id).orElse(null);
            String refusal = checkReviewable(proof, syndic);
            if (refusal == null) {
                refusal = proof.getChargeType() == ChargeType.REGULAR
                        ? settleRegular(proof, syndic)
                        : settleExceptional(proof, syndic);
            }
            if (refusal == null) {
                succeeded.add(id);
            } else {
                failures.add(new ProofBatchResultDTO.Failure(id, refusal));
            }
        }
        return result(proofIds.size(), succeeded, failures, "validée(s)");
    }

    @Override
    @Transactional
    public ProofBatchResultDTO rejectProofs(List<Long> proofIds, String reason) {
        User syndic = getCurrentUser();
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BadRequestException("Le motif du rejet est obligatoire");
        }
        List<Long> succeeded = new ArrayList<>();
        List<ProofBatchResultDTO.Failure> failures = new ArrayList<>();

        for (Long id : new LinkedHashSet<>(proofIds)) {
            ChargePaymentProof proof = proofRepository.findByIdForUpdate(id).orElse(null);
            String refusal = checkReviewable(proof, syndic);
            if (refusal != null) {
                failures.add(new ProofBatchResultDTO.Failure(id, refusal));
                continue;
            }
            reject(proof, syndic, cleanReason);
            succeeded.add(id);

            Long ownerId = proof.getCoOwner().getId();
            String body = "Votre preuve de paiement " + proof.getReference() + " (" + chargeTitleOf(proof)
                    + ") a été rejetée : " + cleanReason + ". La charge reste à payer.";
            AfterCommit.run(() -> notificationService.sendPush(ownerId, "Preuve de paiement rejetée", body));
        }
        return result(proofIds.size(), succeeded, failures, "rejetée(s)");
    }

    // =========================================================================
    // RÈGLEMENT D'UNE CHARGE À LA VALIDATION
    // =========================================================================

    // null si la charge courante est soldée, sinon la raison du refus
    private String settleRegular(ChargePaymentProof proof, User syndic) {
        ChargeCallItem item = chargeCallItemRepository.findByIdForUpdate(proof.getChargeCallItem().getId())
                .orElse(null);
        if (item == null) {
            return "Charge introuvable";
        }
        BigDecimal amount = item.getRemainingAmount();
        if (item.getStatus() == ChargeItemPaymentStatus.PAID || amount.compareTo(BigDecimal.ZERO) <= 0) {
            reject(proof, syndic, "Charge déjà soldée par un autre paiement");
            return "Charge déjà soldée par un autre paiement : preuve classée rejetée";
        }

        ChargeCallPayment payment = new ChargeCallPayment();
        payment.setChargeCallItem(item);
        payment.setOwner(proof.getCoOwner());
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(paidAtOf(proof));
        payment.setOffPlatform(true);
        payment = chargeCallPaymentRepository.save(payment);
        payment.setReference("CPY-" + String.format("%06d", payment.getId()));
        payment = chargeCallPaymentRepository.save(payment);

        item.setPaidAmount((item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO).add(amount));
        item.setStatus(ChargeItemPaymentStatus.PAID);
        item.setProofStatus(ProofStatus.VALIDATED);
        item.setUpdatedAt(LocalDateTime.now());
        chargeCallItemRepository.save(item);

        Residence residence = item.getChargeCall().getBudget().getResidence();
        propertyRepository.findByOwnerIdAndResidenceId(proof.getCoOwner().getId(), residence.getId())
                .forEach(statusRecalculationService::recalculatePropertyDisplayStatus);
        statusRecalculationService.recalculateResidenceHealthStatus(residence);

        markValidated(proof, syndic, payment.getReference(), amount);
        logActivity(residence, proof.getCoOwner(), "CHARGE_CALL_PAYMENT", payment.getId(),
                "Paiement charges validé (hors plateforme)", item.getReference() + " — " + amount.toPlainString() + " FCFA");
        notifyValidated(proof, amount);
        return null;
    }

    // null si la charge exceptionnelle est soldée, sinon la raison du refus
    private String settleExceptional(ChargePaymentProof proof, User syndic) {
        ExceptionalCallItem item = exceptionalCallItemRepository.findByIdForUpdate(proof.getExceptionalCallItem().getId())
                .orElse(null);
        if (item == null) {
            return "Charge introuvable";
        }
        BigDecimal amount = remainingOf(item);
        if (item.getStatus() == ChargeItemPaymentStatus.PAID || amount.compareTo(BigDecimal.ZERO) <= 0) {
            reject(proof, syndic, "Charge déjà soldée par un autre paiement");
            return "Charge déjà soldée par un autre paiement : preuve classée rejetée";
        }

        ExceptionalCallPayment payment = new ExceptionalCallPayment();
        payment.setExceptionalCallItem(item);
        payment.setOwner(proof.getCoOwner());
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(paidAtOf(proof));
        payment.setOffPlatform(true);
        payment = exceptionalCallPaymentRepository.save(payment);
        payment.setReference("ECP-" + String.format("%06d", payment.getId()));
        payment = exceptionalCallPaymentRepository.save(payment);

        item.setPaidAmount((item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO).add(amount));
        item.setStatus(ChargeItemPaymentStatus.PAID);
        item.setProofStatus(ProofStatus.VALIDATED);
        exceptionalCallItemRepository.save(item);

        Residence residence = item.getExceptionalCall().getResidence();
        markValidated(proof, syndic, payment.getReference(), amount);
        logActivity(residence, proof.getCoOwner(), "EXCEPTIONAL_CALL_PAYMENT", payment.getId(),
                "Paiement charge exceptionnelle validé (hors plateforme)",
                item.getExceptionalCall().getTitle() + " — " + amount.toPlainString() + " FCFA");
        notifyValidated(proof, amount);
        return null;
    }

    private void markValidated(ChargePaymentProof proof, User syndic, String paymentReference, BigDecimal amount) {
        proof.setStatus(ProofStatus.VALIDATED);
        proof.setReviewedAt(LocalDateTime.now());
        proof.setReviewedBy(syndic);
        proof.setPaymentReference(paymentReference);
        proof.setAmount(amount);
        proofRepository.save(proof);
    }

    private void reject(ChargePaymentProof proof, User syndic, String reason) {
        proof.setStatus(ProofStatus.REJECTED);
        proof.setReviewedAt(LocalDateTime.now());
        proof.setReviewedBy(syndic);
        proof.setRejectionReason(reason);
        proofRepository.save(proof);
        // La charge redevient « En attente » (statut financier inchangé : elle n'a jamais été PAID)
        if (proof.getChargeCallItem() != null) {
            ChargeCallItem item = proof.getChargeCallItem();
            if (item.getProofStatus() == ProofStatus.PENDING_VALIDATION) {
                item.setProofStatus(ProofStatus.REJECTED);
                chargeCallItemRepository.save(item);
            }
        } else if (proof.getExceptionalCallItem() != null) {
            ExceptionalCallItem item = proof.getExceptionalCallItem();
            if (item.getProofStatus() == ProofStatus.PENDING_VALIDATION) {
                item.setProofStatus(ProofStatus.REJECTED);
                exceptionalCallItemRepository.save(item);
            }
        }
    }

    private void notifyValidated(ChargePaymentProof proof, BigDecimal amount) {
        Long ownerId = proof.getCoOwner().getId();
        String body = "Votre paiement de " + amount.toPlainString() + " FCFA (" + chargeTitleOf(proof)
                + ") a été validé par le syndic.";
        AfterCommit.run(() -> notificationService.sendPush(ownerId, "Paiement validé", body));
    }

    private void logActivity(Residence residence, User actor, String entityType, Long entityId, String message, String detail) {
        ActivityLog activityLog = new ActivityLog();
        activityLog.setResidence(residence);
        activityLog.setType(ActivityType.PAYMENT_RECEIVED);
        activityLog.setRelatedEntityType(entityType);
        activityLog.setRelatedEntityId(entityId);
        activityLog.setActor(actor);
        activityLog.setMessage(message);
        activityLog.setDetail(detail);
        activityLogRepository.save(activityLog);
    }

    // =========================================================================
    // CONTRÔLES
    // =========================================================================

    // null si la preuve peut être validée / rejetée par ce syndic, sinon la raison
    private String checkReviewable(ChargePaymentProof proof, User syndic) {
        if (proof == null || !isSyndicOf(proof, syndic)) {
            return "Preuve introuvable parmi vos résidences";
        }
        if (proof.getStatus() != ProofStatus.PENDING_VALIDATION) {
            return "Preuve déjà " + proof.getStatus().getLabel().toLowerCase();
        }
        return null;
    }

    private static boolean isSyndicOf(ChargePaymentProof proof, User syndic) {
        return proof.getResidence() != null && proof.getResidence().getSyndic() != null
                && proof.getResidence().getSyndic().getId().equals(syndic.getId());
    }

    private static void assertOwner(User coOwner, User current) {
        if (coOwner == null || !coOwner.getId().equals(current.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette charge");
        }
    }

    private static void assertPayable(ChargeItemPaymentStatus status, BigDecimal remaining) {
        if (status == ChargeItemPaymentStatus.PAID) {
            throw new BadRequestException("Cette charge est déjà payée");
        }
        if (status == ChargeItemPaymentStatus.NO_AMOUNT_DUE || remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Aucun montant n'est dû pour cette charge");
        }
    }

    private static String validateDescription(String description) {
        String clean = description == null ? "" : description.trim();
        if (clean.length() < 3) {
            throw new BadRequestException("La description du paiement est obligatoire (3 caractères minimum)");
        }
        if (clean.length() > 1000) {
            throw new BadRequestException("Description : 1000 caractères maximum");
        }
        return clean;
    }

    private static BigDecimal remainingOf(ExceptionalCallItem item) {
        return item.getQuotePart().subtract(item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO);
    }

    // Date du paiement : celle déclarée par le copropriétaire (validée par le syndic), sinon la date de dépôt
    private static LocalDateTime paidAtOf(ChargePaymentProof proof) {
        if (proof.getPaymentDate() != null) {
            return proof.getPaymentDate().atTime(LocalTime.NOON);
        }
        return proof.getSubmittedAt() != null ? proof.getSubmittedAt() : LocalDateTime.now();
    }

    private static ProofBatchResultDTO result(int requested, List<Long> succeeded,
                                              List<ProofBatchResultDTO.Failure> failures, String verb) {
        return ProofBatchResultDTO.builder()
                .requested(requested)
                .succeeded(succeeded.size())
                .failed(failures.size())
                .succeededIds(succeeded)
                .failures(failures)
                .message(succeeded.size() + " preuve(s) " + verb
                        + (failures.isEmpty() ? "" : ", " + failures.size() + " non traitée(s)"))
                .build();
    }

    // =========================================================================
    // MAPPING
    // =========================================================================

    private ChargePaymentProofDTO toDto(ChargePaymentProof proof) {
        Long chargeId = proof.getChargeCallItem() != null ? proof.getChargeCallItem().getId()
                : proof.getExceptionalCallItem() != null ? proof.getExceptionalCallItem().getId() : null;
        String chargeReference = proof.getChargeCallItem() != null ? proof.getChargeCallItem().getReference()
                : proof.getExceptionalCallItem() != null ? proof.getExceptionalCallItem().getReference() : null;
        User owner = proof.getCoOwner();
        Residence residence = proof.getResidence();
        String propertyReference = propertyRepository.findByOwnerIdAndResidenceId(owner.getId(), residence.getId())
                .stream().map(Property::getReference).reduce((a, b) -> a + ", " + b).orElse("");

        return ChargePaymentProofDTO.builder()
                .id(proof.getId())
                .reference(proof.getReference())
                .chargeType(proof.getChargeType())
                .chargeId(chargeId)
                .chargeTitle(chargeTitleOf(proof))
                .chargeReference(chargeReference)
                .residenceId(residence.getId())
                .residenceName(residence.getName())
                .coOwnerId(owner.getId())
                .coOwnerName(fullName(owner))
                .propertyReference(propertyReference)
                .amount(proof.getAmount())
                .paymentMethod(proof.getPaymentMethod())
                .paymentMethodLabel(proof.getPaymentMethod() != null ? proof.getPaymentMethod().getLabel() : null)
                .paymentDate(proof.getPaymentDate())
                .description(proof.getDescription())
                .documentUrl(documentService.urlOf(proof.getDocumentKey()))
                .documentName(proof.getDocumentName())
                .documentContentType(proof.getDocumentContentType())
                .status(proof.getStatus())
                .statusLabel(proof.getStatus().getLabel())
                .submittedAt(proof.getSubmittedAt())
                .reviewedAt(proof.getReviewedAt())
                .reviewedByName(proof.getReviewedBy() != null ? fullName(proof.getReviewedBy()) : null)
                .rejectionReason(proof.getRejectionReason())
                .paymentReference(proof.getPaymentReference())
                .build();
    }

    private static String chargeTitleOf(ChargePaymentProof proof) {
        if (proof.getChargeCallItem() != null) {
            return ChargePeriodLabelUtil.chargeTitle(proof.getChargeCallItem().getChargeCall());
        }
        if (proof.getExceptionalCallItem() != null) {
            return proof.getExceptionalCallItem().getExceptionalCall().getTitle();
        }
        return "";
    }

    private static String fullName(User user) {
        return ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
