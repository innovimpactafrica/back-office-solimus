package com.example.solimus.services.admin.withdrawalRequest;

import com.example.solimus.dtos.admin.withdrawal.*;
import com.example.solimus.entities.Notification;
import com.example.solimus.entities.ProviderProfile;
import com.example.solimus.entities.ProviderWithdrawalRequest;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.SyndicWithdrawalRequest;
import com.example.solimus.entities.User;
import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.WithdrawalMode;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ConflictException;
import com.example.solimus.exceptions.InsufficientBalanceException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.AdminWithdrawalRequestRepository;
import com.example.solimus.repositories.NotificationRepository;
import com.example.solimus.repositories.ProviderProfileRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.SyndicWalletTransactionRepository;
import com.example.solimus.repositories.SyndicWithdrawalRequestRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.repositories.WithdrawalRequestRepository;
import com.example.solimus.services.admin.settings.WithdrawalSettingsService;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.provider.wallet.WalletBalanceService;
import com.example.solimus.services.shared.SyndicTreasuryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalRequestServiceImpl implements WithdrawalRequestService {

    private final SyndicWithdrawalRequestRepository syndicWithdrawalRequestRepository;
    private final WithdrawalRequestRepository providerWithdrawalRequestRepository;
    private final AdminWithdrawalRequestRepository adminWithdrawalRequestRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final WalletBalanceService providerWalletBalanceService;
    private final SyndicProfileRepository syndicProfileRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final UserRepository userRepository;
    private final WithdrawalSettingsService withdrawalSettingsService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final MinioService minioService;
    private final SyndicTreasuryService syndicTreasuryService;

    private static final long MAX_RECEIPT_SIZE_BYTES = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_RECEIPT_CONTENT_TYPES =
            List.of("application/pdf", "image/jpeg", "image/png");

    // ============================================================================
    // BLOC 1 — KPIs
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public WithdrawalDashboardKpiDTO getDashboardKpis() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startOfPreviousMonth = startOfMonth.minusMonths(1);
        LocalDateTime last30Days = now.minusDays(30);

        // --- Total Demandes (tous statuts, cumul) + évolution vs mois précédent ---
        long totalRequests = syndicWithdrawalRequestRepository.count() + providerWithdrawalRequestRepository.count();
        long totalThisMonth = syndicWithdrawalRequestRepository.countByRequestedAtBetween(startOfMonth, now)
                + providerWithdrawalRequestRepository.countByCreatedAtBetween(startOfMonth, now);
        long totalPreviousMonth = syndicWithdrawalRequestRepository.countByRequestedAtBetween(startOfPreviousMonth, startOfMonth)
                + providerWithdrawalRequestRepository.countByCreatedAtBetween(startOfPreviousMonth, startOfMonth);
        Double totalRequestsEvolution = calculatePercentageVariation(totalThisMonth, totalPreviousMonth);

        // --- Demande Validée (COMPLETED, cumul) + évolution vs mois précédent ---
        long validatedRequests = syndicWithdrawalRequestRepository.countByStatus(WithdrawalStatus.COMPLETED)
                + providerWithdrawalRequestRepository.countByStatus(WithdrawalStatus.COMPLETED);
        long validatedThisMonth = syndicWithdrawalRequestRepository.countByStatusAndRequestedAtBetween(WithdrawalStatus.COMPLETED, startOfMonth, now)
                + providerWithdrawalRequestRepository.countByStatusAndCreatedAtBetween(WithdrawalStatus.COMPLETED, startOfMonth, now);
        long validatedPreviousMonth = syndicWithdrawalRequestRepository.countByStatusAndRequestedAtBetween(WithdrawalStatus.COMPLETED, startOfPreviousMonth, startOfMonth)
                + providerWithdrawalRequestRepository.countByStatusAndCreatedAtBetween(WithdrawalStatus.COMPLETED, startOfPreviousMonth, startOfMonth);
        Double validatedRequestsEvolution = calculatePercentageVariation(validatedThisMonth, validatedPreviousMonth);

        // --- Demande en attente (PENDING, cumul, pas d'évolution) ---
        long pendingRequests = syndicWithdrawalRequestRepository.countByStatus(WithdrawalStatus.PENDING)
                + providerWithdrawalRequestRepository.countByStatus(WithdrawalStatus.PENDING);

        // --- Demande refusée (REJECTED, traitées dans les 30 derniers jours) ---
        long rejectedRequests = syndicWithdrawalRequestRepository.countByStatusAndProcessedAtBetween(WithdrawalStatus.REJECTED, last30Days, now)
                + providerWithdrawalRequestRepository.countByStatusAndProcessedAtBetween(WithdrawalStatus.REJECTED, last30Days, now);

        // --- Montant Total (COMPLETED, cumul depuis toujours) ---
        BigDecimal totalAmount = syndicWithdrawalRequestRepository.sumAmountByStatus(WithdrawalStatus.COMPLETED)
                .add(providerWithdrawalRequestRepository.sumAmountByStatus(WithdrawalStatus.COMPLETED));

        return WithdrawalDashboardKpiDTO.builder()
                .totalRequests(totalRequests)
                .totalRequestsEvolution(totalRequestsEvolution)
                .validatedRequests(validatedRequests)
                .validatedRequestsEvolution(validatedRequestsEvolution)
                .pendingRequests(pendingRequests)
                .rejectedRequests(rejectedRequests)
                .totalAmount(totalAmount)
                .build();
    }

    // ============================================================================
    // BLOC 2 — LISTING PAGINÉ (Syndic + Prestataire fusionnés)
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<WithdrawalRequestRowDTO> getAllWithdrawalRequests(String search, WithdrawalStatus status, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : null;
        String statusFilter = status != null ? status.name() : null;

        Page<Object[]> resultPage = adminWithdrawalRequestRepository.searchWithdrawalRequests(searchFilter, statusFilter, pageable);

        return resultPage.map(row -> {

            Long id = ((Number) row[0]).longValue();
            SubscriberType type = SubscriberType.valueOf((String) row[1]);
            String companyName = (String) row[2];

            Timestamp requestedAtTimestamp = (Timestamp) row[3];
            LocalDateTime requestedAt = requestedAtTimestamp != null ? requestedAtTimestamp.toLocalDateTime() : null;

            BigDecimal amount = (BigDecimal) row[4];
            String modeRaw = (String) row[5];
            WithdrawalStatus statusValue = WithdrawalStatus.valueOf((String) row[6]);

            return WithdrawalRequestRowDTO.builder()
                    .id(id)
                    .type(type)
                    .companyName(companyName)
                    .requestedAt(requestedAt)
                    .amount(amount)
                    .mode(resolveModeLabel(type, modeRaw))
                    .status(statusValue)
                    .build();
        });
    }

    // ============================================================================
    // BLOC 3 — DÉTAIL D'UNE DEMANDE DE RETRAIT
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public WithdrawalRequestDetailDTO getWithdrawalRequestDetail(Long id, SubscriberType type) {
        return type == SubscriberType.SYNDIC ? getSyndicWithdrawalDetail(id) : getProviderWithdrawalDetail(id);
    }

    private WithdrawalRequestDetailDTO getSyndicWithdrawalDetail(Long id) {

        SyndicWithdrawalRequest request = syndicWithdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        User syndic = request.getWallet().getSyndic();
        SyndicProfile profile = syndicProfileRepository.findByUserId(syndic.getId()).orElse(null);

        WithdrawalHeaderDTO header = WithdrawalHeaderDTO.builder()
                .photoUrl(syndic.getProfilePhotoUrl())
                .companyName(resolveSyndicDisplayName(syndic, profile))
                .responsibleName(syndic.getFirstName() + " " + syndic.getLastName())
                .accountStatus(syndic.getStatus())
                .specialty(null)
                .city(syndic.getCity())
                .build();

        WithdrawalGeneralInfoDTO generalInfo = WithdrawalGeneralInfoDTO.builder()
                .amount(request.getAmount())
                .requestedAt(request.getRequestedAt())
                .mode(request.getMode() != null ? request.getMode().getLabel() : null)
                .beneficiaryAccount(request.getAccountNumber())
                .reason(request.getReason())
                .build();

        Long walletId = request.getWallet().getId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        // Dernier instant du mois précédent, pas le premier instant du mois courant
        LocalDateTime endOfPreviousMonth = startOfMonth.minusNanos(1);

        // Trésorerie disponible = source unique (SyndicTreasuryService), ne soustrait que les
        // retraits réellement COMPLETED — jamais les PENDING
        BigDecimal currentBalance = syndicTreasuryService.getAvailableBalance(walletId, null);

        BigDecimal soldeFinMoisPrecedent = syndicWalletTransactionRepository.sumTransactionsUpTo(walletId, endOfPreviousMonth);
        Double evolutionPercentage = calculatePercentage(currentBalance.subtract(soldeFinMoisPrecedent), soldeFinMoisPrecedent);

        BigDecimal withdrawnThisMonth = syndicWithdrawalRequestRepository.sumCompletedAmountInPeriod(walletId, startOfMonth, now);
        BigDecimal monthlyLimit = withdrawalSettingsService.getMonthlyLimit().getMonthlyLimit();

        WithdrawalFinancialAnalysisDTO financialAnalysis = WithdrawalFinancialAnalysisDTO.builder()
                .currentBalance(currentBalance)
                .evolutionPercentage(evolutionPercentage)
                .withdrawnThisMonth(withdrawnThisMonth)
                .monthlyLimit(monthlyLimit)
                .build();

        List<SyndicWithdrawalRequest> recent = syndicWithdrawalRequestRepository
                .findByWallet_Syndic_IdAndStatusAndIdNotOrderByProcessedAtDesc(
                        syndic.getId(), WithdrawalStatus.COMPLETED, id, PageRequest.of(0, 5));
        List<RecentWithdrawalRowDTO> recentWithdrawals = recent.stream()
                .map(r -> RecentWithdrawalRowDTO.builder()
                        .date(r.getProcessedAt())
                        .amount(r.getAmount())
                        .status(r.getStatus())
                        .build())
                .toList();

        List<TimelineStepDTO> timeline = buildTimeline(
                request.getRequestedAt(), syndic, request.getStatus(),
                request.getProcessedAt(), request.getProcessedBy(), request.getMotifRefus());

        return WithdrawalRequestDetailDTO.builder()
                .header(header)
                .generalInfo(generalInfo)
                .financialAnalysis(financialAnalysis)
                .recentWithdrawals(recentWithdrawals)
                .timeline(timeline)
                .adminComment(request.getAdminComment())
                .receiptUrl(request.getReceiptUrl())
                .build();
    }

    private WithdrawalRequestDetailDTO getProviderWithdrawalDetail(Long id) {

        ProviderWithdrawalRequest request = providerWithdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        User provider = request.getProvider();
        ProviderProfile profile = providerProfileRepository.findByUserId(provider.getId()).orElse(null);

        WithdrawalHeaderDTO header = WithdrawalHeaderDTO.builder()
                .photoUrl(provider.getProfilePhotoUrl())
                .companyName(resolveProviderDisplayName(provider, profile))
                .responsibleName(provider.getFirstName() + " " + provider.getLastName())
                .accountStatus(provider.getStatus())
                .specialty(profile != null && profile.getSpecialty() != null ? profile.getSpecialty().getName() : null)
                .city(provider.getCity())
                .build();

        WithdrawalGeneralInfoDTO generalInfo = WithdrawalGeneralInfoDTO.builder()
                .amount(request.getAmount())
                .requestedAt(request.getCreatedAt())
                .mode(request.getMethod() != null ? request.getMethod().getLabel() : null)
                .beneficiaryAccount(request.getPhoneNumber())
                // Pas de motif de demande côté prestataire — aucun champ équivalent à "reason"
                .reason(null)
                .build();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime endOfPreviousMonth = startOfMonth.minusNanos(1);

        BigDecimal currentBalance = providerWalletBalanceService.getCurrentBalance(provider.getId());
        BigDecimal soldeFinMoisPrecedent = providerWalletBalanceService.getBalanceAtDate(provider.getId(), endOfPreviousMonth);
        Double evolutionPercentage = calculatePercentage(currentBalance.subtract(soldeFinMoisPrecedent), soldeFinMoisPrecedent);

        BigDecimal withdrawnThisMonth = providerWalletBalanceService.getWithdrawnThisMonth(provider.getId());
        BigDecimal monthlyLimit = withdrawalSettingsService.getMonthlyLimit().getMonthlyLimit();

        WithdrawalFinancialAnalysisDTO financialAnalysis = WithdrawalFinancialAnalysisDTO.builder()
                .currentBalance(currentBalance)
                .evolutionPercentage(evolutionPercentage)
                .withdrawnThisMonth(withdrawnThisMonth)
                .monthlyLimit(monthlyLimit)
                .build();

        List<ProviderWithdrawalRequest> recent = providerWithdrawalRequestRepository
                .findByProviderIdAndStatusAndIdNotOrderByProcessedAtDesc(
                        provider.getId(), WithdrawalStatus.COMPLETED, id, PageRequest.of(0, 5));
        List<RecentWithdrawalRowDTO> recentWithdrawals = recent.stream()
                .map(r -> RecentWithdrawalRowDTO.builder()
                        .date(r.getProcessedAt())
                        .amount(r.getAmount())
                        .status(r.getStatus())
                        .build())
                .toList();

        List<TimelineStepDTO> timeline = buildTimeline(
                request.getCreatedAt(), provider, request.getStatus(),
                request.getProcessedAt(), request.getProcessedBy(), request.getMotifRefus());

        return WithdrawalRequestDetailDTO.builder()
                .header(header)
                .generalInfo(generalInfo)
                .financialAnalysis(financialAnalysis)
                .recentWithdrawals(recentWithdrawals)
                .timeline(timeline)
                .adminComment(request.getAdminComment())
                .receiptUrl(request.getReceiptUrl())
                .build();
    }

    // ============================================================================
    // BLOC 4 — VALIDATION D'UNE DEMANDE
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public WithdrawalValidationSummaryDTO getValidationSummary(Long id, SubscriberType type) {

        if (type == SubscriberType.SYNDIC) {
            SyndicWithdrawalRequest request = syndicWithdrawalRequestRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));
            User syndic = request.getWallet().getSyndic();
            SyndicProfile profile = syndicProfileRepository.findByUserId(syndic.getId()).orElse(null);

            return WithdrawalValidationSummaryDTO.builder()
                    .displayName(resolveSyndicDisplayName(syndic, profile))
                    .amount(request.getAmount())
                    .mode(request.getMode() != null ? request.getMode().getLabel() : null)
                    .build();
        }

        ProviderWithdrawalRequest request = providerWithdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));
        User provider = request.getProvider();
        ProviderProfile profile = providerProfileRepository.findByUserId(provider.getId()).orElse(null);

        return WithdrawalValidationSummaryDTO.builder()
                .displayName(resolveProviderDisplayName(provider, profile))
                .amount(request.getAmount())
                .mode(request.getMethod() != null ? request.getMethod().getLabel() : null)
                .build();
    }

    @Override
    @Transactional
    public WithdrawalActionResultDTO validateWithdrawalRequest(Long id, SubscriberType type, MultipartFile receipt, String comment) {

        validateReceiptFile(receipt);
        String receiptUrl = minioService.uploadFile(receipt, "withdrawal-requests");
        User currentAdmin = getCurrentAdmin();

        if (type == SubscriberType.SYNDIC) {

            SyndicWithdrawalRequest request = syndicWithdrawalRequestRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

            if (request.getStatus() != WithdrawalStatus.PENDING) {
                throw new ConflictException("Cette demande a déjà été traitée");
            }

            // Vérifie que le solde actuel (retraits COMPLETED uniquement, même calcul que le dashboard)
            // couvre bien le montant demandé — seul moment où le blocage se fait, pas à la création de
            // la demande. Si plusieurs demandes PENDING existent pour le même wallet, valider la première
            // fait baisser ce solde : la suivante peut alors être bloquée ici, à raison.
            BigDecimal soldeActuel = syndicTreasuryService.getAvailableBalance(request.getWallet().getId(), null);
            if (request.getAmount().compareTo(soldeActuel) > 0) {
                throw new InsufficientBalanceException(
                        "Solde insuffisant : solde actuel " + soldeActuel + " FCFA, montant demandé "
                                + request.getAmount() + " FCFA");
            }

            request.setStatus(WithdrawalStatus.COMPLETED);
            request.setProcessedAt(LocalDateTime.now());
            request.setProcessedBy(currentAdmin);
            request.setReceiptUrl(receiptUrl);
            request.setAdminComment(comment);
            SyndicWithdrawalRequest saved = syndicWithdrawalRequestRepository.save(request);

            notifyValidated(saved.getWallet().getSyndic(), saved.getAmount());

            return WithdrawalActionResultDTO.builder()
                    .id(saved.getId())
                    .status(saved.getStatus())
                    .processedAt(saved.getProcessedAt())
                    .receiptUrl(saved.getReceiptUrl())
                    .build();
        }

        ProviderWithdrawalRequest request = providerWithdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        if (request.getStatus() != WithdrawalStatus.PENDING) {
            throw new ConflictException("Cette demande a déjà été traitée");
        }

        request.setStatus(WithdrawalStatus.COMPLETED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(currentAdmin);
        request.setReceiptUrl(receiptUrl);
        request.setAdminComment(comment);
        ProviderWithdrawalRequest saved = providerWithdrawalRequestRepository.save(request);

        notifyValidated(saved.getProvider(), saved.getAmount());

        return WithdrawalActionResultDTO.builder()
                .id(saved.getId())
                .status(saved.getStatus())
                .processedAt(saved.getProcessedAt())
                .receiptUrl(saved.getReceiptUrl())
                .build();
    }

    // ============================================================================
    // BLOC 5 — REFUS D'UNE DEMANDE
    // ============================================================================

    @Override
    @Transactional
    public WithdrawalActionResultDTO rejectWithdrawalRequest(Long id, SubscriberType type, RejectWithdrawalRequestDTO dto) {

        User currentAdmin = getCurrentAdmin();

        if (type == SubscriberType.SYNDIC) {

            SyndicWithdrawalRequest request = syndicWithdrawalRequestRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

            if (request.getStatus() != WithdrawalStatus.PENDING) {
                throw new ConflictException("Cette demande a déjà été traitée");
            }

            request.setStatus(WithdrawalStatus.REJECTED);
            request.setProcessedAt(LocalDateTime.now());
            request.setProcessedBy(currentAdmin);
            request.setMotifRefus(dto.getRejectionReason());
            SyndicWithdrawalRequest saved = syndicWithdrawalRequestRepository.save(request);

            notifyRejected(saved.getWallet().getSyndic(), saved.getAmount(), dto.getRejectionReason(), dto.getNotifyUser());

            return WithdrawalActionResultDTO.builder()
                    .id(saved.getId())
                    .status(saved.getStatus())
                    .processedAt(saved.getProcessedAt())
                    .rejectionReason(saved.getMotifRefus())
                    .build();
        }

        ProviderWithdrawalRequest request = providerWithdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        if (request.getStatus() != WithdrawalStatus.PENDING) {
            throw new ConflictException("Cette demande a déjà été traitée");
        }

        request.setStatus(WithdrawalStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(currentAdmin);
        request.setMotifRefus(dto.getRejectionReason());
        ProviderWithdrawalRequest saved = providerWithdrawalRequestRepository.save(request);

        notifyRejected(saved.getProvider(), saved.getAmount(), dto.getRejectionReason(), dto.getNotifyUser());

        return WithdrawalActionResultDTO.builder()
                .id(saved.getId())
                .status(saved.getStatus())
                .processedAt(saved.getProcessedAt())
                .rejectionReason(saved.getMotifRefus())
                .build();
    }

    // ============================================================================
    // BLOC — MÉTHODES UTILITAIRES
    // ============================================================================

    // Vérifie le format (PDF/JPG/PNG) et la taille (max 5 Mo) du reçu de paiement
    private void validateReceiptFile(MultipartFile receipt) {
        if (receipt == null || receipt.isEmpty()) {
            throw new BadRequestException("Le reçu de paiement est obligatoire");
        }
        if (!ALLOWED_RECEIPT_CONTENT_TYPES.contains(receipt.getContentType())) {
            throw new BadRequestException("Format non supporté (PDF, JPG ou PNG uniquement)");
        }
        if (receipt.getSize() > MAX_RECEIPT_SIZE_BYTES) {
            throw new BadRequestException("Fichier trop volumineux (max 5 Mo)");
        }
    }

    // Construit les étapes du "Suivi de la demande" — communes aux deux types de demandeur, seuls
    // les champs source (date de création, demandeur) diffèrent selon l'entité appelante
    private List<TimelineStepDTO> buildTimeline(LocalDateTime createdAt, User requester, WithdrawalStatus status,
                                                 LocalDateTime processedAt, User processedBy, String motifRefus) {
        List<TimelineStepDTO> steps = new ArrayList<>();

        steps.add(TimelineStepDTO.builder()
                .label("Demande créée")
                .date(createdAt)
                .actor(buildActorLabel(requester))
                .completed(true)
                .build());

        switch (status) {
            case PENDING -> steps.add(TimelineStepDTO.builder()
                    .label("En attente de validation")
                    .subtitle("Étape actuelle - Administrateur")
                    .completed(false)
                    .build());
            case COMPLETED -> steps.add(TimelineStepDTO.builder()
                    .label("Demande validée")
                    .date(processedAt)
                    .actor(processedBy != null ? buildActorLabel(processedBy) : null)
                    .completed(true)
                    .build());
            case REJECTED -> steps.add(TimelineStepDTO.builder()
                    .label("Demande refusée")
                    .date(processedAt)
                    .actor(processedBy != null ? buildActorLabel(processedBy) : null)
                    .reason(motifRefus)
                    .completed(true)
                    .build());
        }
        return steps;
    }

    // "Prénom Nom (Rôle)" — jamais une chaîne en dur, toujours construit depuis le compte réel
    private String buildActorLabel(User user) {
        return user.getFirstName() + " " + user.getLastName() + " (" + user.getRole().getName().getLabel() + ")";
    }

    // Notifie le demandeur après validation — notification en base toujours créée, push/email
    // seulement si les notifications sont activées (best-effort, ne bloque jamais la validation)
    private void notifyValidated(User demandeur, BigDecimal amount) {

        String title = "Retrait validé ✅";
        String body = "Votre demande de retrait de " + amount + " FCFA a été validée et le paiement a été effectué.";

        Notification notification = new Notification();
        notification.setUser(demandeur);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRead(false);
        notificationRepository.save(notification);

        if (demandeur.isNotificationsEnabled() && demandeur.getFcmToken() != null) {
            try {
                notificationService.sendPushOnly(demandeur.getId(), title, body);
            } catch (Exception e) {
                log.warn("Échec envoi push (validation retrait) userId={} : {}", demandeur.getId(), e.getMessage());
            }
        }

        if (demandeur.isNotificationsEnabled()) {
            try {
                emailService.sendEmail(demandeur.getEmail(), "Votre retrait a été validé",
                        "Bonjour " + demandeur.getFirstName() + ",\n\n" + body + "\n\nCordialement,\nL'équipe Solimus");
            } catch (Exception e) {
                log.warn("Échec envoi email (validation retrait) userId={} : {}", demandeur.getId(), e.getMessage());
            }
        }
    }

    // Notifie le demandeur après refus — notification en base toujours créée, push/email seulement
    // si notifyUser=true ET les notifications sont activées (best-effort)
    private void notifyRejected(User demandeur, BigDecimal amount, String motifRefus, boolean notifyUser) {

        String title = "Retrait refusé";
        String body = "Votre demande de retrait de " + amount + " FCFA a été refusée. Motif : " + motifRefus;

        Notification notification = new Notification();
        notification.setUser(demandeur);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRead(false);
        notificationRepository.save(notification);

        if (!notifyUser) {
            return;
        }

        if (demandeur.isNotificationsEnabled() && demandeur.getFcmToken() != null) {
            try {
                notificationService.sendPushOnly(demandeur.getId(), title, body);
            } catch (Exception e) {
                log.warn("Échec envoi push (refus retrait) userId={} : {}", demandeur.getId(), e.getMessage());
            }
        }

        if (demandeur.isNotificationsEnabled()) {
            try {
                emailService.sendEmail(demandeur.getEmail(), "Votre retrait a été refusé",
                        "Bonjour " + demandeur.getFirstName() + ",\n\n" + body + "\n\nCordialement,\nL'équipe Solimus");
            } catch (Exception e) {
                log.warn("Échec envoi email (refus retrait) userId={} : {}", demandeur.getId(), e.getMessage());
            }
        }
    }

    // Raison sociale si renseignée, sinon prénom + nom — même règle que partout ailleurs dans l'admin
    private String resolveSyndicDisplayName(User syndic, SyndicProfile profile) {
        return (profile != null && profile.getCompanyName() != null && !profile.getCompanyName().isBlank())
                ? profile.getCompanyName()
                : syndic.getFirstName() + " " + syndic.getLastName();
    }

    private String resolveProviderDisplayName(User provider, ProviderProfile profile) {
        return (profile != null && profile.getCompanyName() != null && !profile.getCompanyName().isBlank())
                ? profile.getCompanyName()
                : provider.getFirstName() + " " + provider.getLastName();
    }

    // Traduit le mode/moyen de paiement brut en libellé FR, selon le type de demandeur
    private String resolveModeLabel(SubscriberType type, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return type == SubscriberType.SYNDIC
                ? WithdrawalMode.valueOf(rawValue).getLabel()
                : PaymentMethod.valueOf(rawValue).getLabel();
    }

    // Calcule un pourcentage, 0% si le total vaut 0 (jamais de division par zéro)
    private Double calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return part.divide(total, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    // Calcule une variation en pourcentage entre deux comptes, 0% si le précédent vaut 0
    private Double calculatePercentageVariation(long current, long previous) {
        if (previous == 0) {
            return 0.0;
        }
        return ((double) (current - previous) / previous) * 100;
    }

    // Récupère l'admin actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur introuvable"));
    }
}