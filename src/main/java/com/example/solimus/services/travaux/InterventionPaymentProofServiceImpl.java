package com.example.solimus.services.travaux;

import com.example.solimus.dtos.payment.proof.InterventionPaymentProofDTO;
import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.dtos.payment.proof.ProviderPaymentProofPageDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ConflictException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ActivityLogRepository;
import com.example.solimus.repositories.InterventionPaymentProofRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.shared.PaymentProofDocumentService;
import com.example.solimus.services.shared.StatusRecalculationService;
import com.example.solimus.utils.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.Set;

/**
 * Règles métier du paiement des travaux HORS plateforme.
 * <ul>
 *   <li>Payeur : celui qui a accepté le devis (syndic ou copropriétaire) ; interventions antérieures
 *       sans trace d'acceptation : le copropriétaire pour les travaux privatifs (mode OWNER, appartement),
 *       le syndic sinon — mêmes règles que le paiement en ligne.</li>
 *   <li>Acompte : prestataire choisi, devis accepté, pas d'acompte déjà enregistré, 0 &lt; montant ≤ TTC.</li>
 *   <li>Solde : travaux terminés (FINISHED), montant = TTC − acompte (aucun paiement partiel), aucun
 *       acompte en attente de confirmation.</li>
 *   <li>Une seule preuve en attente par intervention et par type.</li>
 *   <li>Confirmation par le prestataire sélectionné uniquement : l'acompte est enregistré ; le solde
 *       clôture l'intervention (FINAL_VALIDATION). Aucun wallet n'est mouvementé (fonds hors Solimus) ;
 *       la commission Solimus est prélevée à la fin des travaux (TravauxCommissionService.chargeOnCompletion).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterventionPaymentProofServiceImpl implements InterventionPaymentProofService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<InterventionStatus> DEPOSIT_STATUSES =
            Set.of(InterventionStatus.QUOTE_VALIDATED, InterventionStatus.STARTED, InterventionStatus.FINISHED);

    private final InterventionPaymentProofRepository proofRepository;
    private final InterventionRequestRepository interventionRepository;
    private final PaymentRepository paymentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final StatusRecalculationService statusRecalculationService;
    private final NotificationService notificationService;
    private final PaymentProofDocumentService documentService;

    // =========================================================================
    // PAYEUR : DÉCLARATIONS
    // =========================================================================

    @Override
    @Transactional
    public InterventionPaymentProofDTO declareDeposit(ProofPayerType payerType, Long interventionId, BigDecimal amount,
                                                      String description, OffPlatformPaymentMethod paymentMethod,
                                                      LocalDate paymentDate, MultipartFile document) {
        User payer = getCurrentUser();
        String cleanDescription = validateCommon(description, paymentDate);
        InterventionRequest request = interventionRepository.findByIdForUpdate(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));
        assertPayer(request, payer, payerType);

        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun prestataire n'est sélectionné pour cette intervention");
        }
        if (!DEPOSIT_STATUSES.contains(request.getStatus())) {
            throw new BadRequestException("L'acompte ne peut être déclaré que pour une intervention dont le devis est accepté (statut actuel : "
                    + request.getStatus().getLabel() + ")");
        }
        BigDecimal total = request.getTotalAmount();
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Aucun devis accepté pour cette intervention");
        }
        if (request.getDepositAmount() != null && request.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Un acompte a déjà été versé pour cette intervention");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(total) > 0) {
            throw new BadRequestException("L'acompte doit être compris entre 0 et le montant TTC du devis ("
                    + total.toPlainString() + " FCFA)");
        }
        assertNoPendingProof(interventionId, PaymentType.ACOMPTE, true);
        assertNoPendingProof(interventionId, PaymentType.SOLDE, false);
        assertNoOnlinePaymentInProgress(interventionId);

        InterventionPaymentProof proof = buildProof(request, payer, payerType, PaymentType.ACOMPTE, amount,
                cleanDescription, paymentMethod, paymentDate, document);
        notifyProviderOfDeclaration(proof);
        return toDto(proof);
    }

    @Override
    @Transactional
    public InterventionPaymentProofDTO declareBalance(ProofPayerType payerType, Long interventionId, String description,
                                                      OffPlatformPaymentMethod paymentMethod, LocalDate paymentDate,
                                                      MultipartFile document) {
        User payer = getCurrentUser();
        String cleanDescription = validateCommon(description, paymentDate);
        InterventionRequest request = interventionRepository.findByIdForUpdate(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));
        assertPayer(request, payer, payerType);

        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun prestataire n'est sélectionné pour cette intervention");
        }
        if (request.getStatus() != InterventionStatus.FINISHED) {
            throw new BadRequestException("Les travaux ne sont pas encore terminés");
        }
        if (proofRepository.existsByInterventionRequestIdAndTypeAndStatus(interventionId, PaymentType.ACOMPTE, ProofStatus.PENDING_VALIDATION)) {
            throw new BadRequestException("Un acompte attend la confirmation du prestataire : attendez-la avant de déclarer le solde");
        }
        assertNoPendingProof(interventionId, PaymentType.SOLDE, true);
        assertNoOnlinePaymentInProgress(interventionId);

        BigDecimal balance = request.getRemainingAmount() != null ? request.getRemainingAmount() : BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Aucun solde restant à payer pour cette intervention");
        }

        InterventionPaymentProof proof = buildProof(request, payer, payerType, PaymentType.SOLDE, balance,
                cleanDescription, paymentMethod, paymentDate, document);
        notifyProviderOfDeclaration(proof);
        return toDto(proof);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterventionPaymentProofDTO> getProofsForPayer(ProofPayerType payerType, Long interventionId) {
        User user = getCurrentUser();
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));
        boolean allowed = payerType == ProofPayerType.SYNDIC
                ? request.getResidence() != null && request.getResidence().getSyndic() != null
                  && request.getResidence().getSyndic().getId().equals(user.getId())
                : request.getOwner() != null && request.getOwner().getId().equals(user.getId());
        if (!allowed) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }
        return proofRepository.findByInterventionRequestIdOrderBySubmittedAtDesc(interventionId)
                .stream().map(this::toDto).toList();
    }

    // =========================================================================
    // PRESTATAIRE : CONSULTATION, CONFIRMATION, REJET
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ProviderPaymentProofPageDTO getProofsForProvider(ProofStatus status, String search, int page, int size) {
        User provider = getCurrentUser();
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));
        return ProviderPaymentProofPageDTO.builder()
                .pendingValidationCount(proofRepository.countByProviderIdAndStatus(provider.getId(), ProofStatus.PENDING_VALIDATION))
                .proofs(proofRepository.searchForProvider(provider.getId(), status, normalizedSearch, pageable).map(this::toDto))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public InterventionPaymentProofDTO getProofForProvider(Long proofId) {
        User provider = getCurrentUser();
        InterventionPaymentProof proof = proofRepository.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Preuve de paiement introuvable"));
        if (!proof.getProvider().getId().equals(provider.getId())) {
            throw new ForbiddenException("Ce paiement ne vous est pas destiné");
        }
        return toDto(proof);
    }

    @Override
    @Transactional
    public ProofBatchResultDTO confirmProofs(List<Long> proofIds) {
        User provider = getCurrentUser();
        List<Long> succeeded = new ArrayList<>();
        List<ProofBatchResultDTO.Failure> failures = new ArrayList<>();

        for (Long id : new LinkedHashSet<>(proofIds)) {
            InterventionPaymentProof proof = proofRepository.findByIdForUpdate(id).orElse(null);
            String refusal = checkReviewable(proof, provider);
            if (refusal == null) {
                refusal = settle(proof, provider);
            }
            if (refusal == null) {
                succeeded.add(id);
            } else {
                failures.add(new ProofBatchResultDTO.Failure(id, refusal));
            }
        }
        return result(proofIds.size(), succeeded, failures, "confirmé(s)");
    }

    @Override
    @Transactional
    public ProofBatchResultDTO rejectProofs(List<Long> proofIds, String reason) {
        User provider = getCurrentUser();
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BadRequestException("Le motif du rejet est obligatoire");
        }
        List<Long> succeeded = new ArrayList<>();
        List<ProofBatchResultDTO.Failure> failures = new ArrayList<>();

        for (Long id : new LinkedHashSet<>(proofIds)) {
            InterventionPaymentProof proof = proofRepository.findByIdForUpdate(id).orElse(null);
            String refusal = checkReviewable(proof, provider);
            if (refusal != null) {
                failures.add(new ProofBatchResultDTO.Failure(id, refusal));
                continue;
            }
            markRejected(proof, cleanReason);
            succeeded.add(id);

            Long payerId = proof.getPayer().getId();
            String body = "Le prestataire n'a pas confirmé la réception de votre " + typeLabel(proof.getType()).toLowerCase()
                    + " (" + proof.getInterventionRequest().getTitle() + ") : " + cleanReason;
            AfterCommit.run(() -> notificationService.sendPush(payerId, "Paiement non confirmé", body));
        }
        return result(proofIds.size(), succeeded, failures, "rejeté(s)");
    }

    // =========================================================================
    // CONFIRMATION : ENREGISTREMENT DU PAIEMENT
    // =========================================================================

    // null si le paiement est enregistré, sinon la raison du refus
    private String settle(InterventionPaymentProof proof, User provider) {
        InterventionRequest request = interventionRepository.findByIdForUpdate(proof.getInterventionRequest().getId())
                .orElse(null);
        if (request == null) {
            return "Intervention introuvable";
        }
        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(provider.getId())) {
            return "Vous n'êtes plus le prestataire sélectionné pour cette intervention";
        }

        Optional<PaymentProvider> existing = paymentRepository.findByInterventionRequestIdAndType(request.getId(), proof.getType());
        if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.PENDING) {
            return "Un paiement en ligne est en cours pour cette intervention : attendez son issue";
        }
        if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.COMPLETED) {
            markRejected(proof, "Paiement déjà enregistré par un autre moyen");
            return "Paiement déjà enregistré par un autre moyen : preuve classée rejetée";
        }

        BigDecimal amount = proof.getAmount();
        if (proof.getType() == PaymentType.ACOMPTE) {
            if (request.getDepositAmount() != null && request.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                markRejected(proof, "Un acompte est déjà enregistré pour cette intervention");
                return "Un acompte est déjà enregistré : preuve classée rejetée";
            }
        } else {
            if (request.getStatus() != InterventionStatus.FINISHED) {
                return "L'intervention n'est plus au statut Terminé (statut actuel : " + request.getStatus().getLabel() + ")";
            }
            // Le solde courant fait foi (TTC − acompte) : aucun paiement partiel
            amount = request.getRemainingAmount() != null ? request.getRemainingAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                markRejected(proof, "Aucun solde restant pour cette intervention");
                return "Aucun solde restant : preuve classée rejetée";
            }
        }

        // Paiement enregistré (réutilise une tentative en ligne échouée, sinon nouveau) — marqué hors plateforme
        PaymentProvider payment = existing.orElseGet(() -> PaymentProvider.builder()
                .interventionRequest(request)
                .type(proof.getType())
                .build());
        payment.setProvider(provider);
        payment.setPaymentInitiator(proof.getPayer());
        payment.setAmount(amount);
        payment.setMethod(null);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(paidAtOf(proof));
        payment.setOffPlatform(true);
        payment = paymentRepository.save(payment);
        if (payment.getReference() == null) {
            String prefix = proof.getType() == PaymentType.ACOMPTE ? "PAY-" : "SOL-";
            payment.setReference(prefix + String.format("%06d", payment.getId()));
            payment = paymentRepository.save(payment);
        }

        if (proof.getType() == PaymentType.ACOMPTE) {
            request.setDepositAmount(amount);
            interventionRepository.save(request);
        } else {
            request.setDepositAmount(request.getTotalAmount());
            // Le solde payé valide définitivement les travaux, comme en ligne : action du payeur
            request.addStatusHistory(InterventionStatus.FINAL_VALIDATION, proof.getPayer());
            request.setValidatedAt(LocalDateTime.now());
            interventionRepository.save(request);
            if (request.getResidence() != null) {
                statusRecalculationService.recalculateResidenceHealthStatus(request.getResidence());
            }
            logActivity(request, proof.getPayer(), ActivityType.INTERVENTION_RESOLVED, "INTERVENTION", request.getId(),
                    "Intervention clôturée après paiement — " + request.getTitle(), null);
        }

        proof.setStatus(ProofStatus.VALIDATED);
        proof.setReviewedAt(LocalDateTime.now());
        proof.setAmount(amount);
        proof.setPaymentReference(payment.getReference());
        proofRepository.save(proof);

        logActivity(request, proof.getPayer(), ActivityType.PAYMENT_RECEIVED, "INTERVENTION_PAYMENT", payment.getId(),
                "Paiement intervention hors plateforme confirmé par le prestataire",
                typeLabel(proof.getType()) + " — " + request.getTitle() + " — " + amount.toPlainString() + " FCFA");

        Long payerId = proof.getPayer().getId();
        String body = "Le prestataire a confirmé la réception de votre " + typeLabel(proof.getType()).toLowerCase()
                + " de " + amount.toPlainString() + " FCFA (" + request.getTitle() + ")"
                + (proof.getType() == PaymentType.SOLDE ? " : l'intervention est clôturée." : ".");
        AfterCommit.run(() -> notificationService.sendPush(payerId, "Paiement confirmé", body));
        return null;
    }

    private void markRejected(InterventionPaymentProof proof, String reason) {
        proof.setStatus(ProofStatus.REJECTED);
        proof.setReviewedAt(LocalDateTime.now());
        proof.setRejectionReason(reason);
        proofRepository.save(proof);
    }

    // =========================================================================
    // CONTRÔLES
    // =========================================================================

    private void assertPayer(InterventionRequest request, User user, ProofPayerType payerType) {
        User acceptedBy = request.getQuoteAcceptedBy();
        boolean ownerPaysByDefault = request.getManagementMode() == InterventionManagementMode.OWNER
                && request.getLocationType() != IncidentLocationType.PARTIE_COMMUNE;

        if (payerType == ProofPayerType.COPROPRIETAIRE) {
            if (request.getOwner() == null || !request.getOwner().getId().equals(user.getId())) {
                throw new ForbiddenException("Accès non autorisé à cette intervention");
            }
            boolean ownerPays = acceptedBy != null ? acceptedBy.getId().equals(user.getId()) : ownerPaysByDefault;
            if (!ownerPays) {
                throw new ForbiddenException("Le devis de cette intervention a été validé par le syndic : le paiement est effectué par le syndic.");
            }
        } else {
            if (request.getResidence() == null || request.getResidence().getSyndic() == null
                    || !request.getResidence().getSyndic().getId().equals(user.getId())) {
                throw new ForbiddenException("Vous n'êtes pas autorisé à effectuer ce paiement");
            }
            boolean syndicPays = acceptedBy != null ? acceptedBy.getId().equals(user.getId()) : !ownerPaysByDefault;
            if (!syndicPays) {
                throw new ForbiddenException("Le devis a été accepté par le copropriétaire : l'acompte et le solde sont payés par lui.");
            }
        }
    }

    private void assertNoPendingProof(Long interventionId, PaymentType type, boolean conflict) {
        if (proofRepository.existsByInterventionRequestIdAndTypeAndStatus(interventionId, type, ProofStatus.PENDING_VALIDATION)) {
            String message = (type == PaymentType.ACOMPTE ? "Un acompte" : "Un solde")
                    + " déjà déclaré attend la confirmation du prestataire";
            if (conflict) throw new ConflictException(message);
            throw new BadRequestException(message);
        }
    }

    private void assertNoOnlinePaymentInProgress(Long interventionId) {
        for (PaymentType type : PaymentType.values()) {
            paymentRepository.findByInterventionRequestIdAndType(interventionId, type)
                    .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                    .ifPresent(p -> {
                        throw new BadRequestException("Un paiement en ligne est en cours pour cette intervention : attendez son issue");
                    });
        }
    }

    // null si la preuve peut être confirmée / rejetée par ce prestataire, sinon la raison
    private static String checkReviewable(InterventionPaymentProof proof, User provider) {
        if (proof == null || !proof.getProvider().getId().equals(provider.getId())) {
            return "Preuve introuvable parmi vos paiements";
        }
        if (proof.getStatus() != ProofStatus.PENDING_VALIDATION) {
            return "Preuve déjà " + proof.getStatus().getLabel().toLowerCase();
        }
        return null;
    }

    private static String validateCommon(String description, LocalDate paymentDate) {
        String clean = description == null ? "" : description.trim();
        if (clean.length() < 3) {
            throw new BadRequestException("La description du paiement est obligatoire (3 caractères minimum)");
        }
        if (clean.length() > 1000) {
            throw new BadRequestException("Description : 1000 caractères maximum");
        }
        if (paymentDate != null && paymentDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("La date de paiement ne peut pas être dans le futur");
        }
        return clean;
    }

    // =========================================================================
    // INTERNE
    // =========================================================================

    private InterventionPaymentProof buildProof(InterventionRequest request, User payer, ProofPayerType payerType,
                                                PaymentType type, BigDecimal amount, String description,
                                                OffPlatformPaymentMethod paymentMethod, LocalDate paymentDate,
                                                MultipartFile document) {
        // Fichier déposé après tous les contrôles métier
        PaymentProofDocumentService.StoredDocument stored = documentService.store(document);
        InterventionPaymentProof proof = new InterventionPaymentProof();
        proof.setInterventionRequest(request);
        proof.setType(type);
        proof.setAmount(amount);
        proof.setPayerType(payerType);
        proof.setPayer(payer);
        proof.setProvider(request.getSelectedProvider());
        proof.setPaymentMethod(paymentMethod != null ? paymentMethod : OffPlatformPaymentMethod.AUTRE);
        proof.setPaymentDate(paymentDate);
        proof.setDescription(description);
        proof.setDocumentKey(stored.key());
        proof.setDocumentName(stored.originalName());
        proof.setDocumentContentType(stored.contentType());
        proof.setStatus(ProofStatus.PENDING_VALIDATION);
        proof = proofRepository.save(proof);
        proof.setReference("PTV-" + String.format("%06d", proof.getId()));
        proof = proofRepository.save(proof);
        log.info("Preuve {} ({}) déclarée par {} {} pour l'intervention {}", proof.getReference(), type,
                payerType, payer.getId(), request.getId());
        return proof;
    }

    private void notifyProviderOfDeclaration(InterventionPaymentProof proof) {
        Long providerId = proof.getProvider().getId();
        String body = fullName(proof.getPayer()) + " déclare vous avoir versé " + proof.getAmount().toPlainString()
                + " FCFA (" + typeLabel(proof.getType()).toLowerCase() + ", " + proof.getInterventionRequest().getTitle()
                + ") hors plateforme. Confirmez la réception dans l'application.";
        AfterCommit.run(() -> notificationService.sendPush(providerId, "Paiement à confirmer", body));
    }

    private void logActivity(InterventionRequest request, User actor, ActivityType type, String entityType,
                             Long entityId, String message, String detail) {
        ActivityLog activityLog = new ActivityLog();
        activityLog.setResidence(request.getResidence());
        activityLog.setType(type);
        activityLog.setRelatedEntityType(entityType);
        activityLog.setRelatedEntityId(entityId);
        activityLog.setActor(actor);
        activityLog.setMessage(message);
        activityLog.setDetail(detail);
        activityLogRepository.save(activityLog);
    }

    private static LocalDateTime paidAtOf(InterventionPaymentProof proof) {
        if (proof.getPaymentDate() != null) {
            return proof.getPaymentDate().atTime(LocalTime.NOON);
        }
        return proof.getSubmittedAt() != null ? proof.getSubmittedAt() : LocalDateTime.now();
    }

    private static String typeLabel(PaymentType type) {
        return type == PaymentType.ACOMPTE ? "Acompte" : "Solde";
    }

    private static ProofBatchResultDTO result(int requested, List<Long> succeeded,
                                              List<ProofBatchResultDTO.Failure> failures, String verb) {
        return ProofBatchResultDTO.builder()
                .requested(requested)
                .succeeded(succeeded.size())
                .failed(failures.size())
                .succeededIds(succeeded)
                .failures(failures)
                .message(succeeded.size() + " paiement(s) " + verb
                        + (failures.isEmpty() ? "" : ", " + failures.size() + " non traité(s)"))
                .build();
    }

    private InterventionPaymentProofDTO toDto(InterventionPaymentProof proof) {
        InterventionRequest request = proof.getInterventionRequest();
        return InterventionPaymentProofDTO.builder()
                .id(proof.getId())
                .reference(proof.getReference())
                .interventionId(request.getId())
                .interventionReference(request.getReference())
                .interventionTitle(request.getTitle())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : null)
                .type(proof.getType())
                .typeLabel(typeLabel(proof.getType()))
                .amount(proof.getAmount())
                .quoteTotalAmount(request.getTotalAmount())
                .payerType(proof.getPayerType())
                .payerName(fullName(proof.getPayer()))
                .providerId(proof.getProvider().getId())
                .providerName(fullName(proof.getProvider()))
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
                .rejectionReason(proof.getRejectionReason())
                .paymentReference(proof.getPaymentReference())
                .build();
    }

    private static String fullName(User user) {
        if (user == null) return "";
        return ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
