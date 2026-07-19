package com.example.solimus.services.syndic.finance;

import com.example.solimus.dtos.syndic.dashboard.TreasuryEvolutionPointDTO;
import com.example.solimus.dtos.syndic.finance.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.repositories.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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

        // Calcule le solde actuel du wallet (somme de toutes les transactions jusqu'à maintenant)
        BigDecimal treasuryBrute = calculerSoldeADate(walletId, LocalDateTime.now());
        dto.setTreasuryGlobal(treasuryBrute);

        // Calcule le solde qu'il y avait au début du mois en cours, pour comparer l'évolution
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
                .map(ChargeCallItem::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setUnpaidAmount(unpaidAmount);

        // Calcule le pourcentage d'impayés par rapport au total (collecté + impayé),
        // protection contre la division par zéro
        BigDecimal totalBase = chargesCollected.add(unpaidAmount);
        double unpaidPercent = totalBase.compareTo(BigDecimal.ZERO) > 0
                ? unpaidAmount.divide(totalBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        dto.setUnpaidPercentOfTotal(unpaidPercent);

        // --- Dépenses (mois calendaire en cours, catégorie TRAVAUX) ---

        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1);

        // Somme les transactions de sortie (catégorie TRAVAUX) du mois en cours, en valeur absolue
        BigDecimal expenses = walletId != null
                ? syndicWalletTransactionRepository.sumByCategoryAndPeriod(
                        walletId, WalletTransactionCategory.TRAVAUX, startOfMonth.atStartOfDay(), endOfMonth.atStartOfDay()).abs()
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

        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        if (wallet == null) {
            return List.of();
        }

        // Récupère les transactions de catégorie CHARGES les plus récentes
        List<SyndicWalletTransaction> transactions = syndicWalletTransactionRepository
                .findTopByWalletIdAndCategoryOrderByTransactionDateDesc(
                        wallet.getId(),
                        WalletTransactionCategory.CHARGES,
                        org.springframework.data.domain.PageRequest.of(0, limit)
                );

        return transactions.stream()
                .map(t -> {
                    RecentPaymentDTO dto = new RecentPaymentDTO();
                    dto.setName("Paiement charges"); // À adapter selon les données disponibles
                    dto.setLabel("Charges");
                    dto.setRelativeTime(formatRelativeTime(t.getTransactionDate()));
                    dto.setAmount(t.getAmount().abs());
                    return dto;
                })
                .toList();
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

            // Calcule le solde à la fin de ce mois : par résidence si fournie, sinon global (wallet)
            BigDecimal treasury = (residenceId != null)
                    ? syndicWalletTransactionRepository.sumAllByResidenceId(residenceId, endOfMonth.atStartOfDay())
                    : calculerSoldeADate(walletId, endOfMonth.atStartOfDay());

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
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
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

    // Construit une ligne du tableau "Impayés"
    private UnpaidRowDTO buildUnpaidRow(ChargeCallItem item) {

        LocalDate dueDate = item.getChargeCall().getDueDate();
        long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now());

        UnpaidRowDTO dto = new UnpaidRowDTO();
        dto.setChargeCallItemId(item.getId());
        dto.setCoOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName());
        dto.setResidenceName(item.getChargeCall().getBudget().getResidence().getName());
        dto.setAmountDue(item.getQuotePart());
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
    public Page<FinancePaymentRowDTO> getFinancePayments(int page, int size) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Construit la pagination, triée du paiement le plus récent au plus ancien
        Pageable pageable = PageRequest.of(page, size, Sort.by("paidAt").descending());

        // Récupère les paiements de charges COMPLETED du syndic, directement paginés en base
        Page<ChargeCallPayment> paymentsPage = chargeCallPaymentRepository
                .findByChargeCallItemChargeCallBudgetSyndicIdAndStatus(currentSyndic.getId(), PaymentStatus.COMPLETED, pageable);

        // Transforme chaque paiement en ligne du tableau
        return paymentsPage.map(this::buildFinancePaymentRow);
    }

    // Construit une ligne du tableau "Paiements" (module Finances)
    private FinancePaymentRowDTO buildFinancePaymentRow(ChargeCallPayment payment) {

        ChargeCall chargeCall = payment.getChargeCallItem().getChargeCall();

        FinancePaymentRowDTO dto = new FinancePaymentRowDTO();
        dto.setDate(payment.getPaidAt() != null ? payment.getPaidAt().toLocalDate() : null);
        dto.setCoOwnerName(payment.getOwner().getFirstName() + " " + payment.getOwner().getLastName());
        dto.setResidenceName(chargeCall.getBudget().getResidence().getName());
        // Même format pour mensuel et trimestriel : "Charges T" + periodNumber
        dto.setType("Charges T" + chargeCall.getPeriodNumber());
        dto.setAmount(payment.getAmount());
        dto.setStatus(payment.getStatus() == PaymentStatus.COMPLETED ? "Validé" : payment.getStatus().name());

        return dto;
    }

    // ============================================================
    // LISTE DES IMPAYÉS (module Finances, historique complet)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public UnpaidListResponse getFinanceUnpaid(int page, int size) {

        User currentSyndic = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size, Sort.by("chargeCall.dueDate").ascending());

        // Récupère la page demandée, directement filtrée en base sur les items non soldés
        Page<ChargeCallItem> unpaidPage = chargeCallItemRepository.findUnpaidByBudgetSyndicId(currentSyndic.getId(), pageable);

        // Récupère TOUS les items non soldés (sans pagination), pour calculer les KPI globaux
        List<ChargeCallItem> allUnpaidItems = chargeCallItemRepository.findAllUnpaidByBudgetSyndicId(currentSyndic.getId());

        BigDecimal totalUnpaidAmount = allUnpaidItems.stream()
                .map(ChargeCallItem::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<UnpaidRowDTO> rowDtos = unpaidPage.getContent().stream()
                .map(this::buildUnpaidRow)
                .toList();

        UnpaidListResponse response = new UnpaidListResponse();
        response.setUnpaidCoOwnersCount(allUnpaidItems.size());
        response.setTotalUnpaidAmount(totalUnpaidAmount);
        response.setUnpaidItems(rowDtos);
        response.setCurrentPage(page);
        response.setTotalPages(unpaidPage.getTotalPages());

        return response;
    }

    // ============================================================
    // UTILITAIRES PARTAGÉS
    // ============================================================

    // Calcule le statut d'une ligne de charge selon le nombre de jours de retard
    // (RETARD 1-30j, CRITIQUE 31j+, PARTIEL si paiement partiel dans les délais, PAYE si soldée)
    private String calculateItemStatus(ChargeCallItem item) {

        boolean isFullyPaid = item.getPaidAmount().compareTo(item.getQuotePart()) >= 0;
        if (isFullyPaid) return "PAYE";

        LocalDate dueDate = item.getChargeCall().getDueDate();
        long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now());

        if (daysLate > 30) return "CRITIQUE";
        if (daysLate > 0) return "RETARD";

        boolean hasPartialPayment = item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0;
        if (hasPartialPayment) return "PARTIEL";
        return "A_JOUR";
    }

    // Construit le libellé des biens du copropriétaire pour cette résidence
    private String buildPropertyLabel(ChargeCallItem item) {
        List<Property> properties = propertyRepository.findByOwnerIdAndResidenceId(
                item.getCoOwner().getId(), item.getChargeCall().getBudget().getResidence().getId());

        String propertiesStr = properties.stream()
                .map(Property::getReference)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return propertiesStr + " – " + item.getChargeCall().getBudget().getResidence().getName();
    }
}
