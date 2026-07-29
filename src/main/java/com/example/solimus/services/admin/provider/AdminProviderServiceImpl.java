package com.example.solimus.services.admin.provider;

import com.example.solimus.dtos.admin.provider.*;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.ProviderProfile;
import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.entities.Review;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.ProviderProfileRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.QuoteRepository;
import com.example.solimus.repositories.ReviewRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.shared.ActivityLogPresenter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminProviderServiceImpl implements AdminProviderService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final QuoteRepository quoteRepository;
    private final ReviewRepository reviewRepository;
    private final ActivityLogPresenter activityLogPresenter;

    // Statuts d'intervention considérés "terminée" — même définition que côté résidence
    // (countResolvedByResidenceId)
    private static final List<InterventionStatus> COMPLETED_INTERVENTION_STATUSES =
            List.of(InterventionStatus.FINISHED, InterventionStatus.FINAL_VALIDATION);

    // ============================================================================
    // BLOC — DASHBOARD (KPIs "Gestion des prestataires")
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminProviderDashboardKpiDTO getDashboardKpis() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in30Days = now.plusDays(30);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        // Total prestataires + évolution — comparaison au nombre de comptes déjà inscrits il y a
        // 30 jours (createdAt est une date immuable, contrairement au statut qui n'a pas d'historique)
        long totalProviders = userRepository.countByRole_Name(ERole.ROLE_PRESTATAIRE);
        long totalProvidersThirtyDaysAgo = userRepository.countByRole_NameAndCreatedAtBefore(ERole.ROLE_PRESTATAIRE, thirtyDaysAgo);
        Double totalProvidersVariation = totalProvidersThirtyDaysAgo > 0
                ? (double) (totalProviders - totalProvidersThirtyDaysAgo) / totalProvidersThirtyDaysAgo * 100
                : null;

        // Prestataires actifs / suspendus — comptage direct sur le statut actuel du compte
        long activeProviders = userRepository.countByRole_NameAndStatus(ERole.ROLE_PRESTATAIRE, UserStatus.ACTIVE);
        long suspendedProviders = userRepository.countByRole_NameAndStatus(ERole.ROLE_PRESTATAIRE, UserStatus.DISABLED);

        // Abonnements arrivant à échéance dans les 30 prochains jours
        long expiringIn30Days = providerSubscriptionRepository.countToRenewSoon(now, in30Days);

        // Revenus des abonnements prestataire, depuis toujours (pas de fenêtre de période)
        BigDecimal totalRevenue = providerSubscriptionRepository.sumAmountPaidTotal();

        return AdminProviderDashboardKpiDTO.builder()
                .totalProviders(totalProviders)
                .totalProvidersVariation(totalProvidersVariation)
                .activeProviders(activeProviders)
                // Pas d'historique des changements de statut : impossible de savoir de façon fiable
                // combien étaient "actifs" il y a 30 jours
                .activeProvidersVariation(null)
                .suspendedProviders(suspendedProviders)
                .expiringIn30Days(expiringIn30Days)
                .totalRevenue(totalRevenue)
                .build();
    }

    // ============================================================================
    // BLOC — LISTE DES PRESTATAIRES
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminProviderListResponseDTO getAllProviders(String search, SubscriptionStatus status, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : null;
        String statusFilter = status != null ? status.name() : null;

        Page<Object[]> resultPage = userRepository.searchProviders(searchFilter, statusFilter, pageable);

        List<AdminProviderRowDTO> rows = new ArrayList<>();

        // Chaque ligne est un tableau brut de colonnes, dans l'ordre exact du SELECT (user_id,
        // first_name, last_name, email, phone, city, country, company_name, specialty_name,
        // missions_count, plan_name, expiration_date, subscription_status)
        for (Object[] row : resultPage.getContent()) {

            Long userId = ((Number) row[0]).longValue();
            String firstName = (String) row[1];
            String lastName = (String) row[2];
            String phone = (String) row[4];
            String city = (String) row[5];
            String country = (String) row[6];
            String companyName = (String) row[7];
            String specialtyName = (String) row[8];
            long missionsCount = ((Number) row[9]).longValue();
            String planName = (String) row[10];

            Timestamp expirationTimestamp = (Timestamp) row[11];
            LocalDateTime expirationDate = expirationTimestamp != null ? expirationTimestamp.toLocalDateTime() : null;

            String statusValue = (String) row[12];
            SubscriptionStatus subscriptionStatus = statusValue != null ? SubscriptionStatus.valueOf(statusValue) : null;

            rows.add(AdminProviderRowDTO.builder()
                    .userId(userId)
                    .companyName(companyName)
                    .responsibleName(firstName + " " + lastName)
                    .phone(phone)
                    .specialtyName(specialtyName)
                    .missionsCompletedCount(missionsCount)
                    .planName(planName)
                    .expirationDate(expirationDate)
                    .status(subscriptionStatus)
                    .statusLabel(subscriptionStatus != null ? subscriptionStatus.getLabel() : null)
                    .city(city)
                    .country(country)
                    .build());
        }

        return AdminProviderListResponseDTO.builder()
                .totalCount(resultPage.getTotalElements())
                .providers(rows)
                .currentPage(resultPage.getNumber())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    // ============================================================================
    // BLOC — DÉTAIL D'UN PRESTATAIRE (Blocs A à E)
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public ProviderDetailDTO getProviderDetail(Long providerId) {

        User provider = getProviderOrThrow(providerId);
        ProviderProfile profile = providerProfileRepository.findByUserId(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // ----- Bloc A : En-tête -----
        ProviderHeaderDTO header = ProviderHeaderDTO.builder()
                .photoUrl(provider.getProfilePhotoUrl())
                .companyName(profile.getCompanyName())
                .status(provider.getStatus())
                .statusLabel(provider.getStatus().getLabel())
                .responsibleName(provider.getFirstName() + " " + provider.getLastName())
                .specialtyName(profile.getSpecialty() != null ? profile.getSpecialty().getName() : null)
                .build();

        // ----- Bloc B : KPIs — tout est déjà calculé ailleurs dans le projet, on réutilise -----
        long totalMissionsReceived = interventionRequestRepository.countByNotifiedProvidersId(providerId);
        long quotesSent = quoteRepository.countByProviderId(providerId);
        long missionsCompleted = interventionRequestRepository.countCompletedByProviderId(providerId);
        Double averageInterventionMinutes = reviewRepository.calculerTempsIntervention(providerId);

        ProviderDetailKpiDTO kpis = ProviderDetailKpiDTO.builder()
                .totalMissionsReceived(totalMissionsReceived)
                .quotesSent(quotesSent)
                .missionsCompleted(missionsCompleted)
                .averageInterventionMinutes(averageInterventionMinutes)
                .rating(profile.getRating())
                .reviewCount(profile.getReviewCount())
                .build();

        // ----- Bloc C : Informations générales -----
        ProviderGeneralInfoDTO generalInfo = ProviderGeneralInfoDTO.builder()
                .companyName(profile.getCompanyName())
                .phone(provider.getPhone())
                .address(profile.getAddress())
                .registeredAt(provider.getCreatedAt())
                .email(provider.getEmail())
                .build();

        // ----- Bloc D : Zones d'intervention — un seul champ texte aujourd'hui, rempli par
        // autocomplétion d'adresse (pas une liste de villes séparées à découper) -----
        List<String> interventionZones = (profile.getInterventionZone() != null && !profile.getInterventionZone().isBlank())
                ? List.of(profile.getInterventionZone())
                : List.of();

        // ----- Bloc E : Abonnement en cours -----
        ProviderSubscription subscription = resolveCurrentSubscription(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun abonnement trouvé pour ce prestataire"));

        long daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), subscription.getEndDate());

        ProviderSubscriptionInfoDTO subscriptionInfo = ProviderSubscriptionInfoDTO.builder()
                .subscriptionId(subscription.getId())
                .planName(subscription.getProviderPlan().getName())
                .durationLabel(subscription.getDuration().getLabel())
                .amount(subscription.getAmountPaid())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .daysRemaining(daysRemaining)
                .status(subscription.getStatus())
                .statusLabel(subscription.getStatus().getLabel())
                .build();

        return ProviderDetailDTO.builder()
                .userId(provider.getId())
                .header(header)
                .kpis(kpis)
                .generalInfo(generalInfo)
                .interventionZones(interventionZones)
                .subscription(subscriptionInfo)
                .build();
    }

    // ============================================================================
    // BLOC — HISTORIQUE D'ACTIVITÉ (Bloc F, flux assemblé)
    // ============================================================================
    //
    // Il n'existe aucun journal d'activité dédié côté prestataire (contrairement au syndic et son
    // ActivityLog). On assemble donc le flux à la volée à partir de 4 sources déjà en base : la
    // création du compte, les abonnements réellement payés, les missions terminées et les avis
    // reçus — triées ensemble par date décroissante puis tronquées à "limit".
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProviderActivityRowDTO> getProviderRecentActivities(Long providerId, int limit) {

        User provider = getProviderOrThrow(providerId);
        List<ProviderActivityRowDTO> events = new ArrayList<>();

        // Création du compte
        events.add(buildActivity("Compte créé", "Inscription sur la plateforme", provider.getCreatedAt()));

        // Abonnements réellement payés — le premier de la liste (par date) est la souscription
        // initiale, les suivants sont des renouvellements
        List<ProviderSubscription> subscriptions = providerSubscriptionRepository.findByProviderIdOrderByCreatedAtAsc(providerId);
        for (int i = 0; i < subscriptions.size(); i++) {
            ProviderSubscription subscription = subscriptions.get(i);
            if (subscription.getPaymentStatus() != PaymentStatus.COMPLETED) {
                continue;
            }
            String title = i == 0 ? "Abonnement souscrit" : "Abonnement renouvelé";
            String description = subscription.getProviderPlan().getName() + " — " + subscription.getDuration().getLabel();
            events.add(buildActivity(title, description, subscription.getCreatedAt()));
        }

        // Dernières missions terminées
        List<InterventionRequest> completedInterventions = interventionRequestRepository
                .findBySelectedProviderIdAndStatusInOrderByFinishedAtDesc(providerId, COMPLETED_INTERVENTION_STATUSES, PageRequest.of(0, limit));
        for (InterventionRequest intervention : completedInterventions) {
            if (intervention.getFinishedAt() == null) {
                continue;
            }
            events.add(buildActivity("Intervention terminée",
                    intervention.getTitle() + " — " + intervention.getResidence().getName(),
                    intervention.getFinishedAt()));
        }

        // Derniers avis reçus
        List<Review> reviews = reviewRepository.findByProviderIdOrderByCreatedAtDesc(providerId, PageRequest.of(0, limit));
        for (Review review : reviews) {
            String description = review.getRating() + "/5" + (review.getComment() != null && !review.getComment().isBlank()
                    ? " — " + review.getComment()
                    : "");
            events.add(buildActivity("Avis reçu", description, review.getCreatedAt()));
        }

        // Trie tous les évènements ensemble, les plus récents en premier, puis ne garde que les N demandés
        events.sort((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()));

        return events.stream().limit(limit).toList();
    }

    // ============================================================================
    // BLOC — MÉTHODES UTILITAIRES
    // ============================================================================

    // Récupère un prestataire par son id, en vérifiant que ce compte a bien le rôle PRESTATAIRE —
    // empêche un admin de consulter la "fiche prestataire" d'un compte d'un autre rôle en devinant son id
    private User getProviderOrThrow(Long providerId) {
        User user = userRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Prestataire introuvable"));

        if (user.getRole().getName() != ERole.ROLE_PRESTATAIRE) {
            throw new ResourceNotFoundException("Prestataire introuvable");
        }
        return user;
    }

    // Résout l'abonnement "en cours" d'un prestataire — ACTIVE s'il existe, sinon le plus récent
    // (même règle que partout ailleurs dans l'admin, jamais l'inverse)
    private Optional<ProviderSubscription> resolveCurrentSubscription(Long providerId) {
        return providerSubscriptionRepository.findFirstByProviderIdAndStatus(providerId, SubscriptionStatus.ACTIVE)
                .or(() -> providerSubscriptionRepository.findFirstByProviderIdOrderByEndDateDesc(providerId));
    }

    // Construit une ligne du flux d'activité assemblé
    private ProviderActivityRowDTO buildActivity(String title, String description, LocalDateTime occurredAt) {
        return ProviderActivityRowDTO.builder()
                .title(title)
                .description(description)
                .occurredAt(occurredAt)
                .relativeTime(activityLogPresenter.buildRelativeTime(occurredAt))
                .build();
    }
}
