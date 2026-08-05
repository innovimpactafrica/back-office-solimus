package com.example.solimus.services.admin.finance;

import com.example.solimus.dtos.admin.finance.*;
import com.example.solimus.entities.ProviderPlan;
import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.RevenuePeriod;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.repositories.ProviderPlanRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.SubscriberRepository;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminFinanceServiceImpl implements AdminFinanceService {

    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final SyndicPlanRepository syndicPlanRepository;
    private final ProviderPlanRepository providerPlanRepository;
    private final SubscriberRepository subscriberRepository;

    private static final String[] MONTH_LABELS =
            {"Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sep", "Oct", "Nov", "Déc"};
    private static final String[] QUARTER_LABELS = {"T1", "T2", "T3", "T4"};

    // ============================================================================
    // BLOC 1 — KPIs
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public FinanceDashboardKpiDTO getDashboardKpis() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startOfPreviousMonth = startOfMonth.minusMonths(1);
        LocalDateTime startOfYear = LocalDateTime.of(now.getYear(), 1, 1, 0, 0);
        LocalDateTime in30Days = now.plusDays(30);

        // --- Revenus du mois + évolution vs mois précédent ---
        BigDecimal syndicMonthlyRevenue = sumValidatedRevenue(syndicSubscriptionRepository, startOfMonth, now);
        BigDecimal providerMonthlyRevenue = sumValidatedRevenue(providerSubscriptionRepository, startOfMonth, now);
        BigDecimal monthlyRevenue = syndicMonthlyRevenue.add(providerMonthlyRevenue);

        BigDecimal previousMonthRevenue = sumValidatedRevenue(syndicSubscriptionRepository, startOfPreviousMonth, startOfMonth)
                .add(sumValidatedRevenue(providerSubscriptionRepository, startOfPreviousMonth, startOfMonth));
        Double monthlyRevenueVariation = calculatePercentage(monthlyRevenue.subtract(previousMonthRevenue), previousMonthRevenue);

        // --- Revenus de l'année + % d'avancement de l'objectif (prévision annuelle) ---
        BigDecimal annualRevenue = sumValidatedRevenue(syndicSubscriptionRepository, startOfYear, now)
                .add(sumValidatedRevenue(providerSubscriptionRepository, startOfYear, now));
        int monthsElapsed = now.getMonthValue();
        BigDecimal annualForecast = annualRevenue.compareTo(BigDecimal.ZERO) > 0
                ? annualRevenue.divide(BigDecimal.valueOf(monthsElapsed), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(12))
                : BigDecimal.ZERO;
        Double annualGoalPercentage = calculatePercentage(annualRevenue, annualForecast);

        // --- Paiements reçus (nombre) du mois ---
        long paymentsReceivedCount = syndicSubscriptionRepository.countByPaymentStatusAndCreatedAtBetween(PaymentStatus.COMPLETED, startOfMonth, now)
                + providerSubscriptionRepository.countByPaymentStatusAndCreatedAtBetween(PaymentStatus.COMPLETED, startOfMonth, now);

        // --- Part Syndic / Prestataire dans les revenus du mois ---
        Double syndicRevenueShare = calculatePercentage(syndicMonthlyRevenue, monthlyRevenue);
        Double providerRevenueShare = calculatePercentage(providerMonthlyRevenue, monthlyRevenue);

        // --- Taux de croissance : trimestre en cours vs trimestre précédent ---
        int currentQuarterIndex = (now.getMonthValue() - 1) / 3;
        LocalDateTime currentQuarterStart = LocalDateTime.of(now.getYear(), currentQuarterIndex * 3 + 1, 1, 0, 0);
        LocalDateTime previousQuarterStart = currentQuarterStart.minusMonths(3);

        BigDecimal currentQuarterRevenue = sumValidatedRevenue(syndicSubscriptionRepository, currentQuarterStart, now)
                .add(sumValidatedRevenue(providerSubscriptionRepository, currentQuarterStart, now));
        BigDecimal previousQuarterRevenue = sumValidatedRevenue(syndicSubscriptionRepository, previousQuarterStart, currentQuarterStart)
                .add(sumValidatedRevenue(providerSubscriptionRepository, previousQuarterStart, currentQuarterStart));
        Double growthRate = calculatePercentage(currentQuarterRevenue.subtract(previousQuarterRevenue), previousQuarterRevenue);

        // --- Paiements en attente (nombre + montant), toutes dates confondues ---
        long pendingPaymentsCount = syndicSubscriptionRepository.countByPaymentStatus(PaymentStatus.PENDING)
                + providerSubscriptionRepository.countByPaymentStatus(PaymentStatus.PENDING);
        BigDecimal pendingPaymentsAmount = syndicSubscriptionRepository.sumAmountPaidByPaymentStatus(PaymentStatus.PENDING)
                .add(providerSubscriptionRepository.sumAmountPaidByPaymentStatus(PaymentStatus.PENDING));

        // --- Renouvellements à venir (30 prochains jours) ---
        long renewalsCount = syndicSubscriptionRepository.countToRenewSoon(now, in30Days)
                + providerSubscriptionRepository.countToRenewSoon(now, in30Days);

        return FinanceDashboardKpiDTO.builder()
                .monthlyRevenue(monthlyRevenue)
                .monthlyRevenueVariation(monthlyRevenueVariation)
                .annualRevenue(annualRevenue)
                .annualGoalPercentage(annualGoalPercentage)
                .paymentsReceivedCount(paymentsReceivedCount)
                .syndicRevenue(syndicMonthlyRevenue)
                .syndicRevenueShare(syndicRevenueShare)
                .providerRevenue(providerMonthlyRevenue)
                .providerRevenueShare(providerRevenueShare)
                .growthRate(growthRate)
                .pendingPaymentsCount(pendingPaymentsCount)
                .pendingPaymentsAmount(pendingPaymentsAmount)
                .renewalsCount(renewalsCount)
                .build();
    }

    // ============================================================================
    // BLOC 2 — ÉVOLUTION DES REVENUS (mensuel / trimestriel / annuel)
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public List<RevenueChartPointDTO> getRevenueChart(RevenuePeriod period, Integer year) {

        int targetYear = year != null ? year : LocalDate.now().getYear();

        return switch (period) {
            case MONTHLY -> buildMonthlyChart(targetYear);
            case QUARTERLY -> buildQuarterlyChart(targetYear);
            case YEARLY -> buildYearlyChart();
        };
    }

    private List<RevenueChartPointDTO> buildMonthlyChart(int year) {
        List<RevenueChartPointDTO> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
            LocalDateTime end = start.plusMonths(1);
            result.add(RevenueChartPointDTO.builder()
                    .label(MONTH_LABELS[month - 1])
                    .amount(sumValidatedRevenueBothTypes(start, end))
                    .build());
        }
        return result;
    }

    private List<RevenueChartPointDTO> buildQuarterlyChart(int year) {
        List<RevenueChartPointDTO> result = new ArrayList<>();
        for (int quarter = 0; quarter < 4; quarter++) {
            LocalDateTime start = LocalDateTime.of(year, quarter * 3 + 1, 1, 0, 0);
            LocalDateTime end = start.plusMonths(3);
            result.add(RevenueChartPointDTO.builder()
                    .label(QUARTER_LABELS[quarter])
                    .amount(sumValidatedRevenueBothTypes(start, end))
                    .build());
        }
        return result;
    }

    private List<RevenueChartPointDTO> buildYearlyChart() {
        int earliestYear = findEarliestSubscriptionYear();
        int currentYear = LocalDate.now().getYear();

        List<RevenueChartPointDTO> result = new ArrayList<>();
        for (int year = earliestYear; year <= currentYear; year++) {
            LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0);
            LocalDateTime end = start.plusYears(1);
            result.add(RevenueChartPointDTO.builder()
                    .label(String.valueOf(year))
                    .amount(sumValidatedRevenueBothTypes(start, end))
                    .build());
        }
        return result;
    }

    // Cherche la date de création du tout premier abonnement (Syndic ou Prestataire) jamais créé,
    // pour savoir à partir de quelle année afficher une barre sur le graphique annuel
    private int findEarliestSubscriptionYear() {
        Pageable firstOne = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "createdAt"));

        LocalDateTime earliestSyndic = syndicSubscriptionRepository.findAll(firstOne).stream()
                .findFirst().map(SyndicSubscription::getCreatedAt).orElse(null);
        LocalDateTime earliestProvider = providerSubscriptionRepository.findAll(firstOne).stream()
                .findFirst().map(ProviderSubscription::getCreatedAt).orElse(null);

        LocalDateTime earliest = earliestSyndic;
        if (earliest == null || (earliestProvider != null && earliestProvider.isBefore(earliest))) {
            earliest = earliestProvider;
        }
        return earliest != null ? earliest.getYear() : LocalDate.now().getYear();
    }

    // ============================================================================
    // BLOC 3 — RÉPARTITION DES REVENUS DU MOIS (Syndic vs Prestataire)
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public RevenueSplitDTO getRevenueSplit() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();

        BigDecimal syndicRevenue = sumValidatedRevenue(syndicSubscriptionRepository, startOfMonth, now);
        BigDecimal providerRevenue = sumValidatedRevenue(providerSubscriptionRepository, startOfMonth, now);
        BigDecimal total = syndicRevenue.add(providerRevenue);

        return RevenueSplitDTO.builder()
                .totalRevenue(total)
                .syndics(AmountBreakdownDTO.builder()
                        .amount(syndicRevenue)
                        .percentage(calculatePercentage(syndicRevenue, total))
                        .build())
                .providers(AmountBreakdownDTO.builder()
                        .amount(providerRevenue)
                        .percentage(calculatePercentage(providerRevenue, total))
                        .build())
                .build();
    }

    // ============================================================================
    // BLOC 4 — RÉPARTITION DES REVENUS PAR FORMULE (cumul total, jamais restreint à une période)
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PlanRevenueDTO> getRevenueByPlan() {

        List<PlanRevenueDTO> result = new ArrayList<>();

        for (SyndicPlan plan : syndicPlanRepository.findAll()) {
            result.add(PlanRevenueDTO.builder()
                    .planName(plan.getName())
                    .planType(SubscriberType.SYNDIC)
                    .amount(syndicSubscriptionRepository.sumAmountPaidByPlanId(plan.getId()))
                    // Réutilise le comptage d'abonnés actifs déjà utilisé pour bloquer la suppression
                    // d'une formule encore utilisée (Admin > Abonnements)
                    .subscriberCount(syndicSubscriptionRepository.countBySyndicPlanId(plan.getId()))
                    .build());
        }

        for (ProviderPlan plan : providerPlanRepository.findAll()) {
            result.add(PlanRevenueDTO.builder()
                    .planName(plan.getName())
                    .planType(SubscriberType.PRESTATAIRE)
                    .amount(providerSubscriptionRepository.sumAmountPaidByPlanId(plan.getId()))
                    .subscriberCount(providerSubscriptionRepository.countByProviderPlanId(plan.getId()))
                    .build());
        }

        return result;
    }

    // ============================================================================
    // BLOC 5 — TRANSACTIONS RÉCENTES (pagination Spring Data classique, filtres optionnels)
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionRowDTO> getTransactions(SubscriberType type, String planName, PaymentStatus status,
                                                     int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        String typeFilter = type != null ? type.name() : null;
        String planNameFilter = (planName != null && !planName.isBlank()) ? planName.trim() : null;
        String statusFilter = status != null ? status.name() : null;

        Page<Object[]> resultPage = subscriberRepository.searchTransactions(typeFilter, planNameFilter, statusFilter, pageable);

        return resultPage.map(row -> {

            String reference = (String) row[0];
            String clientName = (String) row[1];
            String typeValue = (String) row[2];
            String planNameValue = (String) row[3];
            BigDecimal amount = (BigDecimal) row[4];
            String paymentMethodValue = (String) row[5];
            String statusValue = (String) row[6];

            Timestamp createdAtTimestamp = (Timestamp) row[7];
            LocalDateTime createdAt = createdAtTimestamp != null ? createdAtTimestamp.toLocalDateTime() : null;

            return TransactionRowDTO.builder()
                    .reference(reference)
                    .clientName(clientName)
                    .type(SubscriberType.valueOf(typeValue))
                    .planName(planNameValue)
                    .amount(amount)
                    .paymentMethod(paymentMethodValue != null ? PaymentMethod.valueOf(paymentMethodValue) : null)
                    .status(statusValue != null ? PaymentStatus.valueOf(statusValue) : null)
                    .createdAt(createdAt)
                    .build();
        });
    }

    // ============================================================================
    // BLOC — MÉTHODES UTILITAIRES
    // ============================================================================

    private BigDecimal sumValidatedRevenueBothTypes(LocalDateTime start, LocalDateTime end) {
        return sumValidatedRevenue(syndicSubscriptionRepository, start, end)
                .add(sumValidatedRevenue(providerSubscriptionRepository, start, end));
    }

    private BigDecimal sumValidatedRevenue(SyndicSubscriptionRepository repository, LocalDateTime start, LocalDateTime end) {
        return repository.sumAmountPaidByPaymentStatusAndCreatedAtBetween(PaymentStatus.COMPLETED, start, end);
    }

    private BigDecimal sumValidatedRevenue(ProviderSubscriptionRepository repository, LocalDateTime start, LocalDateTime end) {
        return repository.sumAmountPaidByPaymentStatusAndCreatedAtBetween(PaymentStatus.COMPLETED, start, end);
    }

    // Calcule un pourcentage part/total, 0% si le total vaut 0 (jamais de division par zéro)
    private Double calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
