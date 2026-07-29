package com.example.solimus.services.admin.dashboard;

import com.example.solimus.dtos.admin.dashboard.*;
import com.example.solimus.entities.ProviderProfile;
import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.repositories.ProviderProfileRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.shared.ActivityLogPresenter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final SyndicProfileRepository syndicProfileRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ActivityLogPresenter activityLogPresenter;

    private static final String[] MONTH_LABELS =
            {"Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sep", "Oct", "Nov", "Déc"};

    // ============================================================================
    // BLOC 1 — KPIs
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardKpiDTO getDashboardKpis() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startOfPreviousMonth = startOfMonth.minusMonths(1);
        LocalDateTime startOfYear = LocalDateTime.of(now.getYear(), 1, 1, 0, 0);

        // Syndics / Prestataires actifs — comptage direct sur le statut actuel du compte
        long activeSyndics = userRepository.countByRole_NameAndStatus(ERole.ROLE_SYNDIC, UserStatus.ACTIVE);
        long activeProviders = userRepository.countByRole_NameAndStatus(ERole.ROLE_PRESTATAIRE, UserStatus.ACTIVE);

        // Résidences / Copropriétaires — comptage global plateforme
        long managedResidences = residenceRepository.count();
        long coOwners = userRepository.countByRole_Name(ERole.ROLE_COPROPRIETAIRE);

        // Abonnements actifs / expirés — Syndic + Prestataire, même requêtes que le dashboard Abonnements
        long activeSubscriptions = syndicSubscriptionRepository.countCurrentlyActive(now)
                + providerSubscriptionRepository.countCurrentlyActive(now);
        long expiredSubscriptions = syndicSubscriptionRepository.countCurrentlyExpiredWithoutRenewal()
                + providerSubscriptionRepository.countCurrentlyExpiredWithoutRenewal();

        // Revenus du mois + évolution vs le mois précédent complet
        BigDecimal monthlyRevenue = syndicSubscriptionRepository.sumAmountPaidInPeriod(startOfMonth, now)
                .add(providerSubscriptionRepository.sumAmountPaidInPeriod(startOfMonth, now));
        BigDecimal previousMonthRevenue = syndicSubscriptionRepository.sumAmountPaidInPeriod(startOfPreviousMonth, startOfMonth)
                .add(providerSubscriptionRepository.sumAmountPaidInPeriod(startOfPreviousMonth, startOfMonth));
        Double monthlyRevenueVariation = calculateRevenueVariation(monthlyRevenue, previousMonthRevenue);

        // Revenus annuels + prévision de fin d'année au rythme actuel
        BigDecimal annualRevenue = syndicSubscriptionRepository.sumAmountPaidInPeriod(startOfYear, now)
                .add(providerSubscriptionRepository.sumAmountPaidInPeriod(startOfYear, now));
        int monthsElapsed = now.getMonthValue();
        BigDecimal annualForecast = annualRevenue.compareTo(BigDecimal.ZERO) > 0
                ? annualRevenue.divide(BigDecimal.valueOf(monthsElapsed), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(12))
                : BigDecimal.ZERO;

        return AdminDashboardKpiDTO.builder()
                .activeSyndics(activeSyndics)
                // Pas d'historique des changements de statut : champ ajouté, rempli plus tard
                .activeSyndicsVariation(null)
                .activeProviders(activeProviders)
                .activeProvidersVariation(null)
                .managedResidences(managedResidences)
                .coOwners(coOwners)
                .activeSubscriptions(activeSubscriptions)
                .expiredSubscriptions(expiredSubscriptions)
                .monthlyRevenue(monthlyRevenue)
                .monthlyRevenueVariation(monthlyRevenueVariation)
                .annualRevenue(annualRevenue)
                .annualForecast(annualForecast)
                .build();
    }

    // ============================================================================
    // BLOC 2 — ÉVOLUTION DES REVENUS (12 MOIS)
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyRevenueDTO> getMonthlyRevenue(Integer year) {

        int targetYear = year != null ? year : LocalDate.now().getYear();
        List<MonthlyRevenueDTO> result = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            LocalDateTime start = LocalDateTime.of(targetYear, month, 1, 0, 0);
            LocalDateTime end = start.plusMonths(1);

            BigDecimal amount = syndicSubscriptionRepository.sumAmountPaidInPeriod(start, end)
                    .add(providerSubscriptionRepository.sumAmountPaidInPeriod(start, end));

            result.add(MonthlyRevenueDTO.builder()
                    .month(month)
                    .label(MONTH_LABELS[month - 1])
                    .amount(amount)
                    .build());
        }
        return result;
    }

    // ============================================================================
    // BLOC 3 — ACTIVITÉ RÉCENTE (flux assemblé, toute la plateforme)
    // ============================================================================
    //
    // Aucun journal d'activité unique ne couvre tous ces évènements à la fois (syndics, prestataires,
    // résidences, copropriétaires). On assemble donc le flux à partir de plusieurs sources déjà en
    // base, chacune limitée à "limit" lignes, puis on trie et tronque l'ensemble.
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PlatformActivityRowDTO> getRecentActivities(int limit) {

        List<PlatformActivityRowDTO> events = new ArrayList<>();

        // Paiements syndic complétés — souscription initiale ("Nouveau syndic") ou renouvellement
        List<SyndicSubscription> completedSyndicPayments = syndicSubscriptionRepository
                .findByPaymentStatusOrderByCreatedAtDesc(PaymentStatus.COMPLETED, PageRequest.of(0, limit));
        for (SyndicSubscription subscription : completedSyndicPayments) {
            boolean isRenewal = syndicSubscriptionRepository
                    .existsBySyndicIdAndCreatedAtBefore(subscription.getSyndic().getId(), subscription.getCreatedAt());
            String clientName = resolveSyndicDisplayName(subscription.getSyndic());

            if (isRenewal) {
                events.add(buildEvent("SUBSCRIPTION_RENEWED", "Abonnement renouvelé",
                        subscription.getSyndicPlan().getName() + " - " + clientName, subscription.getCreatedAt()));
            } else {
                // Pas encore de résidence forcément créée à ce stade — on l'ajoute seulement si elle existe déjà
                String residenceName = residenceRepository.findFirstBySyndicIdOrderByCreatedAtDesc(subscription.getSyndic().getId())
                        .map(Residence::getName).orElse(null);
                String description = clientName + (residenceName != null ? " - " + residenceName : "");
                events.add(buildEvent("NEW_SYNDIC", "Nouveau syndic enregistré", description, subscription.getCreatedAt()));
            }
        }

        // Paiements prestataire complétés — souscription initiale ("Paiement reçu") ou renouvellement
        List<ProviderSubscription> completedProviderPayments = providerSubscriptionRepository
                .findByPaymentStatusOrderByCreatedAtDesc(PaymentStatus.COMPLETED, PageRequest.of(0, limit));
        for (ProviderSubscription subscription : completedProviderPayments) {
            boolean isRenewal = providerSubscriptionRepository
                    .existsByProviderIdAndCreatedAtBefore(subscription.getProvider().getId(), subscription.getCreatedAt());
            String clientName = resolveProviderDisplayName(subscription.getProvider());

            if (isRenewal) {
                events.add(buildEvent("SUBSCRIPTION_RENEWED", "Abonnement renouvelé",
                        subscription.getProviderPlan().getName() + " - " + clientName, subscription.getCreatedAt()));
            } else {
                events.add(buildEvent("PAYMENT_RECEIVED", "Paiement reçu",
                        clientName + " - " + subscription.getAmountPaid() + " FCFA", subscription.getCreatedAt()));
            }
        }

        // Paiements échoués (syndic + prestataire)
        List<SyndicSubscription> failedSyndicPayments = syndicSubscriptionRepository
                .findByPaymentStatusOrderByCreatedAtDesc(PaymentStatus.FAILED, PageRequest.of(0, limit));
        for (SyndicSubscription subscription : failedSyndicPayments) {
            events.add(buildEvent("PAYMENT_FAILED", "Alerte Paiement",
                    "Échec de paiement - " + resolveSyndicDisplayName(subscription.getSyndic()), subscription.getCreatedAt()));
        }
        List<ProviderSubscription> failedProviderPayments = providerSubscriptionRepository
                .findByPaymentStatusOrderByCreatedAtDesc(PaymentStatus.FAILED, PageRequest.of(0, limit));
        for (ProviderSubscription subscription : failedProviderPayments) {
            events.add(buildEvent("PAYMENT_FAILED", "Alerte Paiement",
                    "Échec de paiement - " + resolveProviderDisplayName(subscription.getProvider()), subscription.getCreatedAt()));
        }

        // Prestataires "validés" — pas de date de validation distincte stockée, on prend l'inscription
        List<User> recentProviders = userRepository.findByRole_NameOrderByCreatedAtDesc(ERole.ROLE_PRESTATAIRE, PageRequest.of(0, limit));
        for (User provider : recentProviders) {
            events.add(buildEvent("PROVIDER_VALIDATED", "Prestataire validé",
                    resolveProviderDisplayName(provider), provider.getCreatedAt()));
        }

        // Nouvelles résidences
        List<Residence> recentResidences = residenceRepository
                .findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        for (Residence residence : recentResidences) {
            events.add(buildEvent("NEW_RESIDENCE", "Nouvelle résidence créée", residence.getName(), residence.getCreatedAt()));
        }

        // Nouveaux copropriétaires
        List<User> recentCoOwners = userRepository.findByRole_NameOrderByCreatedAtDesc(ERole.ROLE_COPROPRIETAIRE, PageRequest.of(0, limit));
        for (User coOwner : recentCoOwners) {
            String residenceName = propertyRepository.findAllByOwnerId(coOwner.getId()).stream()
                    .findFirst()
                    .map(property -> property.getResidence().getName())
                    .orElse(null);
            String description = coOwner.getFirstName() + " " + coOwner.getLastName()
                    + (residenceName != null ? " - " + residenceName : "");
            events.add(buildEvent("NEW_COOWNER", "Nouveau copropriétaire", description, coOwner.getCreatedAt()));
        }

        // Trie tous les évènements ensemble, les plus récents en premier, puis ne garde que les N demandés
        events.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return events.stream().limit(limit).toList();
    }

    // ============================================================================
    // BLOC 4 — RÉPARTITION DES UTILISATEURS
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public UserBreakdownDTO getUserBreakdown() {

        long syndics = userRepository.countByRole_Name(ERole.ROLE_SYNDIC);
        long providers = userRepository.countByRole_Name(ERole.ROLE_PRESTATAIRE);
        long coOwners = userRepository.countByRole_Name(ERole.ROLE_COPROPRIETAIRE);
        long total = syndics + providers + coOwners;

        return UserBreakdownDTO.builder()
                .totalUsers(total)
                .syndics(buildRoleBreakdown(syndics, total))
                .providers(buildRoleBreakdown(providers, total))
                .coOwners(buildRoleBreakdown(coOwners, total))
                .build();
    }

    // ============================================================================
    // BLOC 5 — DERNIERS SYNDICS
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public List<RecentSyndicDTO> getRecentSyndics(int limit) {

        // Réutilise directement la recherche déjà construite pour la liste "Admin > Syndics" : sans
        // filtre de recherche/statut, elle est déjà triée par date de création décroissante
        List<Object[]> rows = userRepository.searchSyndics(null, null, PageRequest.of(0, limit)).getContent();

        List<RecentSyndicDTO> result = new ArrayList<>();
        for (Object[] row : rows) {

            Long userId = ((Number) row[0]).longValue();
            String firstName = (String) row[1];
            String lastName = (String) row[2];
            String companyName = (String) row[4];
            long residencesCount = ((Number) row[5]).longValue();
            String planName = (String) row[7];

            String statusValue = (String) row[9];
            SubscriptionStatus subscriptionStatus = statusValue != null ? SubscriptionStatus.valueOf(statusValue) : null;

            String phone = (String) row[10];

            // Raison sociale si renseignée, sinon nom complet du syndic — même règle que partout ailleurs
            String name = (companyName != null && !companyName.isBlank())
                    ? companyName
                    : (firstName + " " + lastName);

            result.add(RecentSyndicDTO.builder()
                    .id(userId)
                    .name(name)
                    .phone(phone)
                    .residencesCount(residencesCount)
                    .planName(planName)
                    .subscriptionStatus(subscriptionStatus)
                    .build());
        }
        return result;
    }

    // ============================================================================
    // BLOC — MÉTHODES UTILITAIRES
    // ============================================================================

    // Calcule une variation en pourcentage, 0% si la valeur précédente est nulle (jamais de division par zéro)
    private Double calculateRevenueVariation(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private RoleBreakdownDTO buildRoleBreakdown(long count, long total) {
        double percentage = total > 0 ? (double) count / total * 100 : 0.0;
        return RoleBreakdownDTO.builder().count(count).percentage(percentage).build();
    }

    // Raison sociale si renseignée, sinon nom complet — même règle que partout ailleurs dans l'admin
    private String resolveSyndicDisplayName(User syndic) {
        SyndicProfile profile = syndicProfileRepository.findByUserId(syndic.getId()).orElse(null);
        return (profile != null && profile.getCompanyName() != null && !profile.getCompanyName().isBlank())
                ? profile.getCompanyName()
                : syndic.getFirstName() + " " + syndic.getLastName();
    }

    private String resolveProviderDisplayName(User provider) {
        ProviderProfile profile = providerProfileRepository.findByUserId(provider.getId()).orElse(null);
        return (profile != null && profile.getCompanyName() != null && !profile.getCompanyName().isBlank())
                ? profile.getCompanyName()
                : provider.getFirstName() + " " + provider.getLastName();
    }

    private PlatformActivityRowDTO buildEvent(String type, String title, String description, LocalDateTime occurredAt) {
        return PlatformActivityRowDTO.builder()
                .type(type)
                .title(title)
                .description(description)
                .createdAt(occurredAt)
                .timeAgo(activityLogPresenter.buildRelativeTime(occurredAt))
                .build();
    }
}
