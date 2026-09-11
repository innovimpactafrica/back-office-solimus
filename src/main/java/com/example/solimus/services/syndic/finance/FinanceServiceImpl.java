package com.example.solimus.services.syndic.finance;

import com.example.solimus.dtos.shared.ExcelFileDTO;
import com.example.solimus.dtos.syndic.charge.PaymentListResponse;
import com.example.solimus.dtos.syndic.charge.PaymentRowDTO;
import com.example.solimus.dtos.syndic.dashboard.TreasuryEvolutionPointDTO;
import com.example.solimus.dtos.syndic.finance.*;
import com.example.solimus.dtos.syndic.residence.WalletTransactionDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.PaymentDelayStatus;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.shared.SyndicTreasuryService;
import com.example.solimus.services.shared.WalletTransactionPresenter;
import com.example.solimus.utils.ExcelExportUtil;
import com.example.solimus.utils.PaymentStatusUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final UserRepository userRepository;
    private final SyndicWalletRepository syndicWalletRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final ChargeCallRepository chargeCallRepository;
    private final PropertyRepository propertyRepository;
    private final ResidenceRepository residenceRepository;
    private final SyndicTreasuryService syndicTreasuryService;
    private final WalletTransactionPresenter walletTransactionPresenter;

    // ============================================================
    // DASHBOARD "FINANCES"
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public FinanceDashboardDTO getFinanceDashboard() {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        FinanceDashboardDTO dto = new FinanceDashboardDTO();

        // --- Trésorerie Globale ---

        // Récupère le wallet du syndic (peut être null si aucun wallet n'a encore été créé)
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        Long walletId = wallet != null ? wallet.getId() : null;

        // Calcule le solde actuel du wallet (somme de toutes les transactions jusqu'à maintenant) —
        // utilisé plus bas pour l'évolution vs mois dernier (flux de transactions seuls)
        BigDecimal treasuryBrute = calculerSoldeADate(walletId, LocalDateTime.now());

        // Trésorerie disponible = source unique (SyndicTreasuryService), ne soustrait que les retraits
        // réellement COMPLETED — jamais les PENDING
        dto.setTreasuryGlobal(syndicTreasuryService.getAvailableBalance(walletId, null));

        // Calcule le solde qu'il y avait au début du mois en cours, pour comparer l'évolution
        // (variation calculée uniquement sur les flux de transactions, sans les retraits réservés)
        LocalDateTime finMoisPrecedent = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal treasuryMoisPrecedent = calculerSoldeADate(walletId, finMoisPrecedent);
        dto.setTreasuryEvolutionPercent(calculerVariation(treasuryBrute, treasuryMoisPrecedent).doubleValue());

        // --- Charges Collectées (trimestre calendaire en cours) ---

        // Calcule les bornes du trimestre calendaire actuel (T1: Jan-Mar, T2: Avr-Jun, etc.)
        LocalDate now = LocalDate.now();
        int currentQuarter = (now.getMonthValue() - 1) / 3;
        LocalDate startOfQuarter = LocalDate.of(now.getYear(), currentQuarter * 3 + 1, 1);
        LocalDate endOfQuarter = startOfQuarter.plusMonths(3);

        // Somme tous les paiements de charges reçus pendant ce trimestre
        BigDecimal chargesCollected = chargeCallPaymentRepository
                .sumByBudgetSyndicIdAndPaidAtBetween(currentSyndic.getId(), startOfQuarter.atStartOfDay(), endOfQuarter.atStartOfDay());
        dto.setChargesCollected(chargesCollected);

        // --- Impayés ---

        // Récupère toutes les lignes de charges non soldées, toutes résidences du syndic
        List<ChargeCallItem> allUnpaidItems = chargeCallItemRepository.findAllUnpaidByBudgetSyndicId(currentSyndic.getId());
        BigDecimal unpaidAmount = allUnpaidItems.stream()
                .map(item -> item.getRemainingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setUnpaidAmount(unpaidAmount);

        // Calcule le pourcentage d'impayés par rapport au total (collecté + impayé),
        // protection contre la division par zéro
        BigDecimal totalBase = chargesCollected.add(unpaidAmount);
        double unpaidPercent = totalBase.compareTo(BigDecimal.ZERO) > 0
                ? unpaidAmount.divide(totalBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        dto.setUnpaidPercentOfTotal(unpaidPercent);

        // --- Dépenses (mois calendaire en cours, catégorie BUDGET_EXPENSE) ---

        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1);

        // Somme les transactions de sortie (catégorie BUDGET_EXPENSE) du mois en cours, en valeur absolue
        BigDecimal expenses = walletId != null
                ? syndicWalletTransactionRepository.sumByCategoryAndPeriod(
                        walletId, WalletTransactionCategory.BUDGET_EXPENSE, startOfMonth.atStartOfDay(), endOfMonth.atStartOfDay()).abs()
                : BigDecimal.ZERO;
        dto.setExpenses(expenses);

        // --- Graphique Trésorerie vs Appels de charges (cumulatif, 6 DERNIERS MOIS GLISSANTS) ---
        dto.setTreasuryEvolution(buildTreasuryEvolutionGlobal(currentSyndic.getId(), walletId));

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentPaymentDTO> getRecentPayments(int limit) {
        User currentSyndic = getCurrentUser();

        Pageable pageable = PageRequest.of(0, limit, Sort.by("paidAt").descending());

        // Récupère les paiements de charges COMPLETED les plus récents — même source que
        // /finances/payments, pour que le lien "Voir tous les paiements" reste cohérent
        Page<ChargeCallPayment> recentPage = chargeCallPaymentRepository
                .findByChargeCallItemChargeCallBudgetSyndicIdAndStatus(currentSyndic.getId(), PaymentStatus.COMPLETED, pageable);

        return recentPage.getContent().stream()
                .map(this::buildRecentPaymentRow)
                .toList();
    }

    // Construit une ligne "Paiements Récents" (dashboard Finances) — aperçu minimal mais identifiable :
    // qui a payé, pour quelle résidence, pour quelle période
    private RecentPaymentDTO buildRecentPaymentRow(ChargeCallPayment payment) {

        ChargeCall chargeCall = payment.getChargeCallItem().getChargeCall();

        RecentPaymentDTO dto = new RecentPaymentDTO();
        dto.setCoOwnerName(payment.getOwner().getFirstName() + " " + payment.getOwner().getLastName());
        dto.setResidenceName(chargeCall.getBudget().getResidence().getName());
        dto.setPeriod(buildSimplePeriodeLabel(chargeCall));
        dto.setYear(chargeCall.getYear());
        dto.setLabel("Charges");
        dto.setRelativeTime(formatRelativeTime(payment.getPaidAt()));
        dto.setAmount(payment.getAmount());

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TreasuryEvolutionPointDTO> getTreasuryEvolution(Long residenceId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère le wallet du syndic (peut être null si aucun wallet n'a encore été créé)
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        Long walletId = wallet != null ? wallet.getId() : null;

        // Construit le graphique : filtré par résidence si fournie, sinon global (wallet)
        return buildTreasuryEvolution(currentSyndic.getId(), walletId, residenceId);
    }

    // Construit le graphique cumulatif "Trésorerie vs Appels de charges" sur les 6 DERNIERS MOIS GLISSANTS.
    // Si residenceId est fourni, filtre trésorerie et appels de charges sur cette résidence ;
    // sinon, calcule sur toutes les résidences du syndic (wallet global).
    private List<TreasuryEvolutionPointDTO> buildTreasuryEvolution(Long syndicId, Long walletId, Long residenceId) {

        // Tableau des libellés de mois affichés sur le graphique
        String[] monthLabels = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        List<TreasuryEvolutionPointDTO> result = new ArrayList<>();

        // Récupère les ChargeCall : d'une résidence précise, ou de tout le syndic si résidence absente
        List<ChargeCall> allChargeCalls = (residenceId != null)
                ? chargeCallRepository.findByBudgetResidenceId(residenceId)
                : chargeCallRepository.findByBudgetSyndicId(syndicId);

        // Calcule le premier mois à afficher : 5 mois avant le mois actuel (6 mois au total)
        LocalDate now = LocalDate.now();
        LocalDate startMonth = now.withDayOfMonth(1).minusMonths(5);

        // Parcourt les 6 derniers mois, du plus ancien au plus récent
        for (int i = 0; i < 6; i++) {

            LocalDate currentMonthDate = startMonth.plusMonths(i);
            LocalDate endOfMonth = currentMonthDate.plusMonths(1);

            // Trésorerie disponible à la fin de ce mois (source unique SyndicTreasuryService) — déduit
            // les retraits COMPLETED traités jusqu'à cette date, cohérent avec la carte KPI du dashboard
            BigDecimal treasury = syndicTreasuryService.getAvailableBalanceAsOf(walletId, residenceId, endOfMonth.atStartOfDay());

            // Additionne tous les ChargeCall créés avant la fin de ce mois (cumul progressif)
            BigDecimal chargeCallsCumulated = allChargeCalls.stream()
                    .filter(cc -> cc.getCreatedAt().isBefore(endOfMonth.atStartOfDay()))
                    .map(ChargeCall::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            TreasuryEvolutionPointDTO point = new TreasuryEvolutionPointDTO();
            point.setMonthLabel(monthLabels[currentMonthDate.getMonthValue() - 1]);
            point.setTreasury(treasury);
            point.setChargeCallsCumulated(chargeCallsCumulated);

            result.add(point);
        }

        // Retourne les 6 points construits, du plus ancien au plus récent
        return result;
    }

    // Construit le graphique cumulatif "Trésorerie vs Appels de charges", toutes résidences du syndic confondues.
    // Affiche les 6 DERNIERS MOIS GLISSANTS (se terminant au mois actuel), et non pas toujours Jan-Jun,
    // pour que le graphique reste pertinent peu importe la période de l'année où le syndic consulte le dashboard.
    private List<TreasuryEvolutionPointDTO> buildTreasuryEvolutionGlobal(Long syndicId, Long walletId) {
        return buildTreasuryEvolution(syndicId, walletId, null);
    }

    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    private BigDecimal calculerSoldeADate(Long walletId, LocalDateTime asOfDate) {
        if (walletId == null) return BigDecimal.ZERO;
        return syndicWalletTransactionRepository.sumTransactionsUpTo(walletId, asOfDate);
    }

    private BigDecimal calculerVariation(BigDecimal actuel, BigDecimal precedent) {
        if (precedent.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return actuel.subtract(precedent)
                .divide(precedent, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    private String formatRelativeTime(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        long hours = java.time.Duration.between(dateTime, now).toHours();
        if (hours < 24) {
            return "Il y a " + hours + "h";
        }
        long days = hours / 24;
        if (days < 7) {
            return "Il y a " + days + "j";
        }
        long weeks = days / 7;
        return "Il y a " + weeks + " sem";
    }

    // ============================================================
    // LISTE DES IMPAYÉS (module Finances, historique complet)
    // ============================================================

    // Construit une ligne du tableau "Impayés" (module Finances) — mêmes champs que le module
    // Charges, plus dueDate (spécifique au module Finances)
    private UnpaidRowDTO buildUnpaidRow(ChargeCallItem item) {

        ChargeCall chargeCall = item.getChargeCall();
        LocalDate dueDate = chargeCall.getDueDate();
        long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now());

        UnpaidRowDTO dto = new UnpaidRowDTO();
        dto.setChargeCallItemId(item.getId());
        dto.setCoOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName());
        dto.setPropertyLabel(buildPropertyReferences(item));
        dto.setResidenceName(chargeCall.getBudget().getResidence().getName());
        dto.setPeriod(buildSimplePeriodeLabel(chargeCall));
        dto.setYear(chargeCall.getYear());
        dto.setAmountDue(item.getTotalDue()); // quote-part + pénalité si déjà appliquée
        dto.setDueDate(dueDate);
        dto.setDaysLate((int) Math.max(daysLate, 0));
        dto.setStatus(calculateItemStatus(item));

        return dto;
    }

    // ============================================================
    // LISTE DES PAIEMENTS (module Finances, historique complet)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public PaymentListResponse getFinancePayments(Long residenceId, Integer year, int page, int size, String search) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Si une résidence est précisée, vérifie qu'elle appartient bien à ce syndic
        if (residenceId != null) {
            Residence residence = residenceRepository.findById(residenceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));
            if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
                throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("chargeCall.createdAt").descending());

        // Aligné intégralement sur /api/syndic/budget/payments : même source (ChargeCallItem PAID),
        // mêmes filtres résidence/année/recherche
        Page<ChargeCallItem> itemsPage = chargeCallItemRepository.findPaidBySyndicIdWithFilters(
                currentSyndic.getId(), residenceId, year, search, pageable);

        List<PaymentRowDTO> rowDtos = itemsPage.getContent().stream()
                .map(this::buildPaymentRow)
                .toList();

        PaymentListResponse response = new PaymentListResponse();
        response.setTotalPayments((int) itemsPage.getTotalElements());
        response.setPayments(rowDtos);
        response.setCurrentPage(page);
        response.setTotalPages(itemsPage.getTotalPages());

        return response;
    }

    // Construit une ligne du tableau "Paiements" (module Finances) — mêmes champs que le module Charges
    private PaymentRowDTO buildPaymentRow(ChargeCallItem item) {

        ChargeCall chargeCall = item.getChargeCall();

        PaymentRowDTO dto = new PaymentRowDTO();
        dto.setCoOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName());
        dto.setPropertyLabel(buildPropertyReferences(item));
        dto.setResidenceName(chargeCall.getBudget().getResidence().getName());
        dto.setPeriod(buildSimplePeriodeLabel(chargeCall));
        dto.setYear(chargeCall.getYear());
        dto.setAmountDue(item.getTotalDue()); // quote-part + pénalité si déjà appliquée
        dto.setAmountPaid(item.getPaidAmount());
        dto.setBalance(item.getRemainingAmount());
        dto.setStatus(calculateItemStatus(item));

        chargeCallPaymentRepository.findFirstByChargeCallItemIdOrderByPaidAtDesc(item.getId())
                .ifPresent(p -> dto.setPaymentDate(p.getPaidAt() != null ? p.getPaidAt().toLocalDate() : null));

        return dto;
    }

    // ============================================================
    // LISTE DES IMPAYÉS (module Finances, historique complet)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public UnpaidListResponse getFinanceUnpaid(Long residenceId, Integer year, int page, int size) {

        User currentSyndic = getCurrentUser();

        // Si une résidence est précisée, vérifie qu'elle appartient bien à ce syndic
        if (residenceId != null) {
            Residence residence = residenceRepository.findById(residenceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));
            if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
                throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("chargeCall.dueDate").ascending());

        // Récupère la page demandée, directement filtrée en base sur les items non soldés
        Page<ChargeCallItem> unpaidPage = chargeCallItemRepository.findUnpaidBySyndicIdWithFilters(
                currentSyndic.getId(), residenceId, year, pageable);

        // Récupère TOUS les items non soldés (sans pagination, mêmes filtres), pour calculer les KPI globaux
        List<ChargeCallItem> allUnpaidItems = chargeCallItemRepository.findAllUnpaidBySyndicIdWithFilters(
                currentSyndic.getId(), residenceId, year);

        BigDecimal totalUnpaidAmount = allUnpaidItems.stream()
                .map(item -> item.getRemainingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Nombre de copropriétaires distincts concernés (un même copropriétaire peut avoir
        // plusieurs lignes impayées — ex: plusieurs trimestres — et ne doit être compté qu'une fois ici)
        long distinctCoOwners = allUnpaidItems.stream()
                .map(item -> item.getCoOwner().getId())
                .distinct()
                .count();

        List<UnpaidRowDTO> rowDtos = unpaidPage.getContent().stream()
                .map(this::buildUnpaidRow)
                .toList();

        UnpaidListResponse response = new UnpaidListResponse();
        response.setUnpaidItemsCount(allUnpaidItems.size());
        response.setDistinctUnpaidCoOwnersCount((int) distinctCoOwners);
        response.setTotalUnpaidAmount(totalUnpaidAmount);
        response.setUnpaidItems(rowDtos);
        response.setCurrentPage(page);
        response.setTotalPages(unpaidPage.getTotalPages());

        return response;
    }

    // ============================================================
    // TRANSACTIONS WALLET (historique complet, "Voir l'historique complet")
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionDTO> getWalletTransactions(Long residenceId, WalletTransactionCategory category, Integer year, int page, int size) {

        User currentSyndic = getCurrentUser();

        // Si une résidence est précisée, vérifie qu'elle appartient bien à ce syndic
        if (residenceId != null) {
            Residence residence = residenceRepository.findById(residenceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));
            if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
                throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
            }
        }

        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        if (wallet == null) {
            return Page.empty(PageRequest.of(page, size));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<SyndicWalletTransaction> transactionPage = syndicWalletTransactionRepository
                .findByWalletIdWithFilters(wallet.getId(), category, year, residenceId, pageable);

        return transactionPage.map(walletTransactionPresenter::toDTO);
    }

    // ============================================================
    // EXPORTS EXCEL (mêmes filtres que la liste paginée correspondante — sans pagination,
    // toutes les lignes filtrées sont incluses)
    // ============================================================

    private static final String[] PAYMENTS_EXPORT_HEADERS =
            {"Copropriétaire", "Lot", "Résidence", "Période", "Montant payé", "Statut", "Date paiement"};
    private static final String[] UNPAID_EXPORT_HEADERS =
            {"Copropriétaire", "Lot", "Résidence", "Période", "Statut", "Montant dû", "Échéance", "Jours de retard"};
    private static final String[] TRANSACTIONS_EXPORT_HEADERS =
            {"Libellé", "Payeur / Prestataire", "Lot", "Résidence", "Catégorie", "Montant", "Mode", "Référence", "Date"};

    private static final String PAYMENTS_HEADER_COLOR = "C8E6C9";
    private static final String UNPAID_HEADER_COLOR = "F5C6C6";
    private static final String TRANSACTIONS_HEADER_COLOR = "C5D5F7";

    @Override
    @Transactional(readOnly = true)
    public ExcelFileDTO exportPayments(Long residenceId, Integer year, String search) {

        User currentSyndic = getCurrentUser();
        Residence residence = checkResidenceOwnership(residenceId, currentSyndic);

        // Toutes les lignes filtrées, sans pagination (Pageable.unpaged), même tri que la liste paginée
        Page<ChargeCallItem> itemsPage = chargeCallItemRepository.findPaidBySyndicIdWithFilters(
                currentSyndic.getId(), residenceId, year, search,
                Pageable.unpaged(Sort.by("chargeCall.createdAt").descending()));

        List<Object[]> rows = itemsPage.getContent().stream()
                .map(item -> {
                    ChargeCall chargeCall = item.getChargeCall();
                    LocalDate paymentDate = chargeCallPaymentRepository
                            .findFirstByChargeCallItemIdOrderByPaidAtDesc(item.getId())
                            .map(p -> p.getPaidAt() != null ? p.getPaidAt().toLocalDate() : null)
                            .orElse(null);
                    return new Object[]{
                            item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName(),
                            buildPropertyReferences(item),
                            chargeCall.getBudget().getResidence().getName(),
                            buildSimplePeriodeLabel(chargeCall) + " " + chargeCall.getYear(),
                            item.getPaidAmount(),
                            calculateItemStatus(item),
                            paymentDate
                    };
                })
                .toList();

        byte[] content = ExcelExportUtil.generate("Paiements", PAYMENTS_EXPORT_HEADERS, rows, PAYMENTS_HEADER_COLOR, Set.of(4));
        String fileName = "paiements_" + residenceSegment(residence) + "_" + yearSegment(year) + ".xlsx";

        return new ExcelFileDTO(fileName, content);
    }

    @Override
    @Transactional(readOnly = true)
    public ExcelFileDTO exportUnpaid(Long residenceId, Integer year) {

        User currentSyndic = getCurrentUser();
        Residence residence = checkResidenceOwnership(residenceId, currentSyndic);

        List<ChargeCallItem> allUnpaidItems = chargeCallItemRepository
                .findAllUnpaidBySyndicIdWithFilters(currentSyndic.getId(), residenceId, year)
                .stream()
                .sorted(Comparator.comparing(item -> item.getChargeCall().getDueDate()))
                .toList();

        List<Object[]> rows = allUnpaidItems.stream()
                .map(item -> {
                    ChargeCall chargeCall = item.getChargeCall();
                    LocalDate dueDate = chargeCall.getDueDate();
                    long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
                    return new Object[]{
                            item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName(),
                            buildPropertyReferences(item),
                            chargeCall.getBudget().getResidence().getName(),
                            buildSimplePeriodeLabel(chargeCall) + " " + chargeCall.getYear(),
                            calculateItemStatus(item),
                            item.getTotalDue(),
                            dueDate,
                            (int) Math.max(daysLate, 0)
                    };
                })
                .toList();

        byte[] content = ExcelExportUtil.generate("Impayés", UNPAID_EXPORT_HEADERS, rows, UNPAID_HEADER_COLOR, Set.of(5));
        String fileName = "impayes_" + residenceSegment(residence) + "_" + yearSegment(year) + ".xlsx";

        return new ExcelFileDTO(fileName, content);
    }

    @Override
    @Transactional(readOnly = true)
    public ExcelFileDTO exportWalletTransactions(Long residenceId, WalletTransactionCategory category, Integer year) {

        User currentSyndic = getCurrentUser();
        Residence residence = checkResidenceOwnership(residenceId, currentSyndic);

        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);

        // ORDER BY déjà porté par la requête (transactionDate DESC) — pas besoin de Sort ici
        List<WalletTransactionDTO> transactions = wallet == null
                ? List.of()
                : syndicWalletTransactionRepository
                        .findByWalletIdWithFilters(wallet.getId(), category, year, residenceId, Pageable.unpaged())
                        .getContent().stream()
                        .map(walletTransactionPresenter::toDTO)
                        .toList();

        List<Object[]> rows = transactions.stream()
                .map(t -> new Object[]{
                        t.getLabel(),
                        t.getPayerOrPayeeName(),
                        t.getPropertyReference(),
                        t.getResidenceName(),
                        t.getCategory() != null ? t.getCategory().name() : null,
                        t.getAmount(),
                        t.getMode(),
                        t.getReference(),
                        t.getTransactionDate()
                })
                .toList();

        byte[] content = ExcelExportUtil.generate("Transactions", TRANSACTIONS_EXPORT_HEADERS, rows, TRANSACTIONS_HEADER_COLOR, Set.of(5));
        String fileName = "transactions_" + residenceSegment(residence) + "_" + categorySegment(category) + "_" + yearSegment(year) + ".xlsx";

        return new ExcelFileDTO(fileName, content);
    }

    // ============================================================
    // UTILITAIRES PARTAGÉS
    // ============================================================

    // Calcule le statut d'une ligne de charge — PAYE si soldée, sinon délègue le seuil de retard
    // à PaymentStatusUtils (seule source de vérité). PARTIEL supprimé : aucun paiement partiel
    // n'est plus autorisé, un item non soldé a donc toujours paidAmount = 0.
    private String calculateItemStatus(ChargeCallItem item) {

        boolean isFullyPaid = item.getPaidAmount().compareTo(item.getTotalDue()) >= 0;
        if (isFullyPaid) return "PAYE";

        LocalDate dueDate = item.getChargeCall().getDueDate();
        PaymentDelayStatus delayStatus = PaymentStatusUtils.computeDelayStatus(dueDate, false, LocalDate.now());
        return PaymentStatusUtils.toLabel(delayStatus);
    }

    // Construit la liste des lots du copropriétaire pour cette résidence (résidence exclue —
    // portée par le champ residenceName séparé du DTO)
    private String buildPropertyReferences(ChargeCallItem item) {
        List<Property> properties = propertyRepository.findByOwnerIdAndResidenceId(
                item.getCoOwner().getId(), item.getChargeCall().getBudget().getResidence().getId());

        return properties.stream()
                .map(Property::getReference)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    // Libellé court de la période d'un appel de charges, ex: "T3" (trimestriel) ou "Jan" (mensuel)
    private String buildSimplePeriodeLabel(ChargeCall chargeCall) {
        if (chargeCall.getFrequency() != null && chargeCall.getFrequency().name().equals("TRIMESTRIEL")) {
            return "T" + chargeCall.getPeriodNumber();
        }
        String[] mois = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        int index = chargeCall.getPeriodNumber() - 1;
        return (index >= 0 && index < mois.length) ? mois[index] : "P" + chargeCall.getPeriodNumber();
    }

    // Vérifie que la résidence (si fournie) appartient bien au syndic connecté, et la retourne
    // (null si aucun filtre résidence) — réutilisé par les 3 exports pour construire le nom de fichier
    private Residence checkResidenceOwnership(Long residenceId, User currentSyndic) {
        if (residenceId == null) return null;
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }
        return residence;
    }

    private String residenceSegment(Residence residence) {
        return residence != null ? slugify(residence.getName()) : "toutes-residences";
    }

    private String yearSegment(Integer year) {
        return year != null ? year.toString() : "toutes-annees";
    }

    private String categorySegment(WalletTransactionCategory category) {
        return category != null ? category.name().toLowerCase() : "toutes-categories";
    }

    // Normalise un texte pour un nom de fichier : sans accents, sans espaces ni caractères spéciaux
    private String slugify(String input) {
        String withoutAccents = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
