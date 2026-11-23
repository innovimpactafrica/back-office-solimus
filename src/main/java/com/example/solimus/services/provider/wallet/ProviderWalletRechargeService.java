package com.example.solimus.services.provider.wallet;

import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.dtos.provider.wallet.recharge.AdminDirectRechargeDTO;
import com.example.solimus.dtos.provider.wallet.recharge.AdminRechargePageDTO;
import com.example.solimus.dtos.provider.wallet.recharge.ProviderWalletRechargeDTO;
import com.example.solimus.dtos.provider.wallet.recharge.RechargeInfoDTO;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletRecharge;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofStatus;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.enums.RechargeOrigin;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ProviderWalletRechargeRepository;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.provider.eligibility.ProviderEligibilityService;
import com.example.solimus.services.shared.PaymentProofDocumentService;
import com.example.solimus.utils.AfterCommit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Recharge du wallet prestataire, sans paiement dans l'application.
 * <ol>
 *   <li>Le prestataire paie Solimus hors application (instructions : {@code app.provider.recharge.instructions}),
 *       puis envoie sa preuve (montant, moyen, date, description, justificatif PDF/JPG/PNG).</li>
 *   <li>L'admin valide une ou plusieurs preuves : le wallet est crédité (ligne RECHARGE du grand livre) et le
 *       prestataire notifié. Ou il rejette avec un motif : rien n'est crédité.</li>
 *   <li>L'admin peut aussi créditer directement un prestataire (paiement reçu au bureau, geste commercial).</li>
 * </ol>
 * Le solde rechargé ouvre l'accès aux demandes de travaux (solde &gt; seuil) et paie les commissions Solimus
 * sur les travaux réglés hors plateforme. Ce n'est pas un revenu de la plateforme : il n'entre pas dans le
 * wallet Solimus (qui ne contient que les commissions).
 */
@Slf4j
@Service
public class ProviderWalletRechargeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProviderWalletRechargeRepository rechargeRepository;
    private final ProviderWalletRepository walletRepository;
    private final ProviderWalletTransactionRepository ledger;
    private final UserRepository userRepository;
    private final PaymentProofDocumentService documentService;
    private final NotificationService notificationService;
    private final ProviderEligibilityService eligibilityService;
    private final WalletBalanceService walletBalanceService;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;
    private final int maxPending;
    private final String instructions;

    public ProviderWalletRechargeService(ProviderWalletRechargeRepository rechargeRepository,
                                         ProviderWalletRepository walletRepository,
                                         ProviderWalletTransactionRepository ledger,
                                         UserRepository userRepository,
                                         PaymentProofDocumentService documentService,
                                         NotificationService notificationService,
                                         ProviderEligibilityService eligibilityService,
                                         WalletBalanceService walletBalanceService,
                                         @Value("${app.provider.recharge.min-amount:500}") BigDecimal minAmount,
                                         @Value("${app.provider.recharge.max-amount:5000000}") BigDecimal maxAmount,
                                         @Value("${app.provider.recharge.max-pending:3}") int maxPending,
                                         @Value("${app.provider.recharge.instructions:Payez Solimus par Wave, Orange Money ou virement, puis envoyez la preuve de paiement. Votre compte est crédité après vérification.}") String instructions) {
        this.rechargeRepository = rechargeRepository;
        this.walletRepository = walletRepository;
        this.ledger = ledger;
        this.userRepository = userRepository;
        this.documentService = documentService;
        this.notificationService = notificationService;
        this.eligibilityService = eligibilityService;
        this.walletBalanceService = walletBalanceService;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.maxPending = Math.max(1, maxPending);
        this.instructions = instructions;
    }

    // =========================================================================
    // PRESTATAIRE
    // =========================================================================

    @Transactional(readOnly = true)
    public RechargeInfoDTO getInfo() {
        User provider = currentUser();
        var eligibility = eligibilityService.getEligibility(provider);
        return RechargeInfoDTO.builder()
                .walletBalance(eligibility.getWalletBalance())
                .minimumBalanceExclusive(eligibility.getMinimumBalanceExclusive())
                .eligible(eligibility.isEligible())
                .missingAmount(eligibility.getMissingAmount())
                .minRechargeAmount(minAmount)
                .maxRechargeAmount(maxAmount)
                .instructions(instructions)
                .pendingCount(rechargeRepository.countByProviderIdAndStatus(provider.getId(), ProofStatus.PENDING_VALIDATION))
                .maxPendingRecharges(maxPending)
                .build();
    }

    @Transactional
    public ProviderWalletRechargeDTO submit(BigDecimal amount, OffPlatformPaymentMethod paymentMethod,
                                            LocalDate paymentDate, String description, MultipartFile document) {
        User provider = currentUser();
        checkAmount(amount);
        String cleanDescription = checkDescription(description);
        if (paymentDate != null && paymentDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("La date du paiement ne peut pas être dans le futur");
        }
        if (rechargeRepository.countByProviderIdAndStatus(provider.getId(), ProofStatus.PENDING_VALIDATION) >= maxPending) {
            throw new BadRequestException("Vous avez déjà " + maxPending + " recharge(s) en attente de validation : "
                    + "attendez leur traitement avant d'en envoyer une autre");
        }
        PaymentProofDocumentService.StoredDocument stored = documentService.store(document);

        ProviderWalletRecharge recharge = ProviderWalletRecharge.builder()
                .provider(provider)
                .amount(amount)
                .paymentMethod(paymentMethod != null ? paymentMethod : OffPlatformPaymentMethod.AUTRE)
                .paymentDate(paymentDate != null ? paymentDate : LocalDate.now())
                .description(cleanDescription)
                .documentKey(stored.key())
                .documentName(stored.originalName())
                .documentContentType(stored.contentType())
                .origin(RechargeOrigin.PREUVE_PRESTATAIRE)
                .status(ProofStatus.PENDING_VALIDATION)
                .build();
        recharge = assignReference(recharge);
        log.info("Recharge {} soumise par le prestataire {} : {} FCFA", recharge.getReference(), provider.getId(), amount);
        return toDto(recharge);
    }

    @Transactional(readOnly = true)
    public Page<ProviderWalletRechargeDTO> getMyRecharges(ProofStatus status, int page, int size) {
        User provider = currentUser();
        return rechargeRepository.findForProvider(provider.getId(), status, pageOf(page, size)).map(this::toDto);
    }

    // =========================================================================
    // ADMIN
    // =========================================================================

    @Transactional(readOnly = true)
    public AdminRechargePageDTO listForAdmin(ProofStatus status, String search, int page, int size) {
        String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return AdminRechargePageDTO.builder()
                .pendingValidationCount(rechargeRepository.countByStatus(ProofStatus.PENDING_VALIDATION))
                .recharges(rechargeRepository.searchForAdmin(status, pattern, pageOf(page, size)).map(this::toDto))
                .build();
    }

    @Transactional(readOnly = true)
    public ProviderWalletRechargeDTO getForAdmin(Long id) {
        return rechargeRepository.findWithProviderById(id).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Recharge introuvable"));
    }

    @Transactional
    public ProofBatchResultDTO validate(List<Long> ids) {
        User admin = currentUser();
        List<Long> succeeded = new ArrayList<>();
        List<ProofBatchResultDTO.Failure> failures = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            ProviderWalletRecharge recharge = rechargeRepository.findByIdForUpdate(id).orElse(null);
            String refusal = checkPending(recharge);
            if (refusal != null) {
                failures.add(new ProofBatchResultDTO.Failure(id, refusal));
                continue;
            }
            credit(recharge, admin);
            succeeded.add(id);
        }
        return result(ids.size(), succeeded, failures, "validée(s), wallet(s) crédité(s)");
    }

    @Transactional
    public ProofBatchResultDTO reject(List<Long> ids, String reason) {
        User admin = currentUser();
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BadRequestException("Le motif du rejet est obligatoire");
        }
        List<Long> succeeded = new ArrayList<>();
        List<ProofBatchResultDTO.Failure> failures = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            ProviderWalletRecharge recharge = rechargeRepository.findByIdForUpdate(id).orElse(null);
            String refusal = checkPending(recharge);
            if (refusal != null) {
                failures.add(new ProofBatchResultDTO.Failure(id, refusal));
                continue;
            }
            recharge.setStatus(ProofStatus.REJECTED);
            recharge.setReviewedAt(LocalDateTime.now());
            recharge.setReviewedBy(admin);
            recharge.setRejectionReason(cleanReason);
            rechargeRepository.save(recharge);
            succeeded.add(id);

            Long providerId = recharge.getProvider().getId();
            String body = "Votre recharge " + recharge.getReference() + " de " + recharge.getAmount().toPlainString()
                    + " FCFA a été refusée : " + cleanReason + ".";
            AfterCommit.run(() -> notificationService.sendPush(providerId, "Recharge refusée", body));
        }
        return result(ids.size(), succeeded, failures, "rejetée(s)");
    }

    /** Crédit immédiat par l'admin (paiement reçu hors application), tracé comme une recharge validée. */
    @Transactional
    public ProviderWalletRechargeDTO creditDirectly(AdminDirectRechargeDTO dto) {
        User admin = currentUser();
        User provider = userRepository.findById(dto.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestataire introuvable"));
        if (provider.getRole() == null || provider.getRole().getName() != ERole.ROLE_PRESTATAIRE) {
            throw new BadRequestException("Cet utilisateur n'est pas un prestataire");
        }
        checkAmount(dto.getAmount());
        ProviderWalletRecharge recharge = ProviderWalletRecharge.builder()
                .provider(provider)
                .amount(dto.getAmount())
                .paymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : OffPlatformPaymentMethod.AUTRE)
                .paymentDate(LocalDate.now())
                .description(checkDescription(dto.getDescription()))
                .origin(RechargeOrigin.CREDIT_ADMIN)
                .status(ProofStatus.PENDING_VALIDATION)
                .build();
        recharge = assignReference(recharge);
        credit(recharge, admin);
        log.info("Crédit direct {} de {} FCFA sur le wallet du prestataire {} par l'admin {}",
                recharge.getReference(), dto.getAmount(), provider.getId(), admin.getId());
        return toDto(recharge);
    }

    // =========================================================================
    // RÈGLES
    // =========================================================================

    // Crédite le wallet (verrouillé) et marque la recharge validée. Appelé uniquement sur une recharge PENDING
    // verrouillée : walletTransactionId empêche tout double crédit.
    private void credit(ProviderWalletRecharge recharge, User admin) {
        User provider = recharge.getProvider();
        ProviderWallet wallet = walletRepository.findByProviderIdForUpdate(provider.getId())
                .orElseGet(() -> walletRepository.save(ProviderWallet.builder().provider(provider).build()));

        String method = recharge.getPaymentMethod() != null ? recharge.getPaymentMethod().getLabel() : "Autre";
        ProviderWalletTransaction line = new ProviderWalletTransaction();
        line.setWallet(wallet);
        line.setCategory(ProviderWalletTransactionCategory.RECHARGE);
        line.setAmount(recharge.getAmount());
        line.setLabel("Recharge du compte — " + method);
        line.setReference(recharge.getReference());
        line.setTransactionDate(LocalDateTime.now());
        line = ledger.save(line);

        recharge.setStatus(ProofStatus.VALIDATED);
        recharge.setReviewedAt(LocalDateTime.now());
        recharge.setReviewedBy(admin);
        recharge.setWalletTransactionId(line.getId());
        rechargeRepository.save(recharge);

        Long providerId = provider.getId();
        String amount = recharge.getAmount().toPlainString();
        String reference = recharge.getReference();
        AfterCommit.run(() -> {
            BigDecimal balance = walletBalanceService.getCurrentBalance(providerId);
            notificationService.sendPush(providerId, "Compte rechargé",
                    amount + " FCFA crédités sur votre wallet (" + reference + "). Nouveau solde : "
                            + (balance != null ? balance.toPlainString() : "?") + " FCFA.");
        });
    }

    private static String checkPending(ProviderWalletRecharge recharge) {
        if (recharge == null) return "Recharge introuvable";
        if (recharge.getStatus() != ProofStatus.PENDING_VALIDATION || recharge.getWalletTransactionId() != null) {
            return "Recharge déjà traitée (" + recharge.getStatus().getLabel() + ")";
        }
        return null;
    }

    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Le montant de la recharge doit être positif");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException("Le montant doit être un nombre entier de FCFA");
        }
        if (amount.compareTo(minAmount) < 0) {
            throw new BadRequestException("Recharge minimum : " + minAmount.toPlainString() + " FCFA");
        }
        if (amount.compareTo(maxAmount) > 0) {
            throw new BadRequestException("Recharge maximum : " + maxAmount.toPlainString() + " FCFA");
        }
    }

    private static String checkDescription(String description) {
        String clean = description == null ? "" : description.trim();
        if (clean.length() < 3 || clean.length() > 1000) {
            throw new BadRequestException("La description doit contenir entre 3 et 1000 caractères");
        }
        return clean;
    }

    private ProviderWalletRecharge assignReference(ProviderWalletRecharge recharge) {
        ProviderWalletRecharge saved = rechargeRepository.save(recharge);
        saved.setReference("RCH-" + String.format("%06d", saved.getId()));
        return rechargeRepository.save(saved);
    }

    private static ProofBatchResultDTO result(int requested, List<Long> succeeded,
                                              List<ProofBatchResultDTO.Failure> failures, String verb) {
        return ProofBatchResultDTO.builder()
                .requested(requested)
                .succeeded(succeeded.size())
                .failed(failures.size())
                .succeededIds(succeeded)
                .failures(failures)
                .message(succeeded.size() + " recharge(s) " + verb
                        + (failures.isEmpty() ? "" : ", " + failures.size() + " non traitée(s)"))
                .build();
    }

    private static PageRequest pageOf(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private ProviderWalletRechargeDTO toDto(ProviderWalletRecharge r) {
        User p = r.getProvider();
        return ProviderWalletRechargeDTO.builder()
                .id(r.getId())
                .reference(r.getReference())
                .providerId(p != null ? p.getId() : null)
                .providerName(p != null ? (p.getFirstName() + " " + p.getLastName()).trim() : null)
                .providerEmail(p != null ? p.getEmail() : null)
                .providerPhone(p != null ? p.getPhone() : null)
                .amount(r.getAmount())
                .paymentMethod(r.getPaymentMethod())
                .paymentMethodLabel(r.getPaymentMethod() != null ? r.getPaymentMethod().getLabel() : null)
                .paymentDate(r.getPaymentDate())
                .description(r.getDescription())
                .documentUrl(r.getDocumentKey() != null ? documentService.urlOf(r.getDocumentKey()) : null)
                .documentName(r.getDocumentName())
                .documentContentType(r.getDocumentContentType())
                .origin(r.getOrigin())
                .originLabel(r.getOrigin() != null ? r.getOrigin().getLabel() : null)
                .status(r.getStatus())
                .statusLabel(r.getStatus() != null ? r.getStatus().getLabel() : null)
                .submittedAt(r.getSubmittedAt())
                .reviewedAt(r.getReviewedAt())
                .reviewedByName(r.getReviewedBy() != null
                        ? (r.getReviewedBy().getFirstName() + " " + r.getReviewedBy().getLastName()).trim() : null)
                .rejectionReason(r.getRejectionReason())
                .build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
