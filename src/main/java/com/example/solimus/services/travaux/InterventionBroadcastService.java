package com.example.solimus.services.travaux;

import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.ProviderProfile;
import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.ProviderProfileRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.QuoteRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.provider.eligibility.ProviderEligibilityService;
import com.example.solimus.utils.AfterCommit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * ============================================================================
 * DIFFUSION PROGRESSIVE D'UNE DEMANDE DE TRAVAUX AUX PRESTATAIRES (« au plus proche d'abord »)
 * ============================================================================
 * Comme une application de VTC : la demande est d'abord proposée aux prestataires situés dans un
 * petit rayon autour de la résidence (5 km), puis le rayon s'élargit par paliers (10, 15, 20, 30 km)
 * tant qu'aucun prestataire n'est choisi et que trop peu de devis ont été reçus.
 * <p>
 * Un prestataire reçoit la demande s'il :
 * <ul>
 *   <li>a la spécialité demandée et un profil actif,</li>
 *   <li>a un abonnement en cours,</li>
 *   <li>a un wallet strictement supérieur au seuil (ProviderEligibilityService),</li>
 *   <li>est dans le rayon courant : position GPS récente, sinon zone de référence de son profil.</li>
 * </ul>
 * Chaque passage n'ajoute que les prestataires pas encore notifiés : un prestataire devenu éligible
 * (wallet rechargé, abonnement renouvelé) reçoit la demande au palier suivant. La liste
 * {@code notifiedProviders} reste la seule source de vérité de « qui peut voir la demande ».
 * <p>
 * Configuration (propriétés {@code app.travaux.broadcast.*}) :
 * radii-km (5,10,15,20,30), step-minutes (10), stop-after-quotes (3, 0 = jamais), max-age-hours (72).
 */
@Slf4j
@Service
@Lazy(false) // profil docker en lazy-initialization : le job planifié doit exister dès le démarrage
public class InterventionBroadcastService {

    /** Statuts où une demande attend encore des devis. */
    private static final List<InterventionStatus> OPEN_FOR_QUOTES =
            List.of(InterventionStatus.PENDING, InterventionStatus.SYNDIC_ASSIGNED);

    private final InterventionRequestRepository interventionRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final com.example.solimus.services.shared.SubscriptionPolicy subscriptionPolicy;
    private final QuoteRepository quoteRepository;
    private final ProviderEligibilityService eligibilityService;
    private final GeolocationService geolocationService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    private final List<Double> radiiKm;
    private final long stepMinutes;
    private final long stopAfterQuotes;
    private final long maxAgeHours;
    private final int gpsFreshnessMinutes;
    private final boolean jobEnabled;

    public InterventionBroadcastService(InterventionRequestRepository interventionRepository,
                                        ProviderProfileRepository providerProfileRepository,
                                        ProviderSubscriptionRepository providerSubscriptionRepository,
                                        com.example.solimus.services.shared.SubscriptionPolicy subscriptionPolicy,
                                        QuoteRepository quoteRepository,
                                        ProviderEligibilityService eligibilityService,
                                        GeolocationService geolocationService,
                                        NotificationService notificationService,
                                        EmailService emailService,
                                        PlatformTransactionManager transactionManager,
                                        @Value("${app.travaux.broadcast.radii-km:5,10,15,20,30}") String radiiKm,
                                        @Value("${app.travaux.broadcast.step-minutes:10}") long stepMinutes,
                                        @Value("${app.travaux.broadcast.stop-after-quotes:3}") long stopAfterQuotes,
                                        @Value("${app.travaux.broadcast.max-age-hours:72}") long maxAgeHours,
                                        @Value("${provider.gps.freshness-minutes:60}") int gpsFreshnessMinutes,
                                        @Value("${app.travaux.broadcast.job-enabled:true}") boolean jobEnabled) {
        this.interventionRepository = interventionRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.providerSubscriptionRepository = providerSubscriptionRepository;
        this.subscriptionPolicy = subscriptionPolicy;
        this.quoteRepository = quoteRepository;
        this.eligibilityService = eligibilityService;
        this.geolocationService = geolocationService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.radiiKm = parseRadii(radiiKm);
        this.stepMinutes = Math.max(1, stepMinutes);
        this.stopAfterQuotes = Math.max(0, stopAfterQuotes);
        this.maxAgeHours = Math.max(1, maxAgeHours);
        this.gpsFreshnessMinutes = gpsFreshnessMinutes;
        this.jobEnabled = jobEnabled;
    }

    // =========================================================================
    // API
    // =========================================================================

    /** Premier rayon de diffusion (km). */
    public double initialRadiusKm() {
        return radiiKm.get(0);
    }

    /** Rayon maximal de diffusion (km). */
    public double maxRadiusKm() {
        return radiiKm.get(radiiKm.size() - 1);
    }

    public long stepMinutes() {
        return stepMinutes;
    }

    /**
     * Lance la diffusion d'une demande : premier palier. Sans effet (0) si elle est déjà diffusée.
     * Doit être appelé dans la transaction qui a créé / modifié la demande.
     *
     * @return nombre de prestataires notifiés à ce palier
     */
    @Transactional
    public int startBroadcast(InterventionRequest request) {
        Residence residence = request.getResidence();
        if (residence == null || residence.getLatitude() == null || residence.getLongitude() == null) {
            throw new BadRequestException("La résidence n'a pas de coordonnées GPS, impossible de trouver des prestataires proches");
        }
        if (request.getBroadcastStartedAt() != null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        request.setBroadcastStartedAt(now);
        request.setLastBroadcastAt(now);
        request.setBroadcastRadiusKm(initialRadiusKm());
        int notified = notifyEligibleProvidersWithin(request, initialRadiusKm());
        interventionRepository.save(request);
        log.info("Diffusion de la demande {} lancée : {} prestataire(s) à moins de {} km", request.getId(),
                notified, initialRadiusKm());
        return notified;
    }

    /** true si la demande a déjà été diffusée (même si aucun prestataire n'était encore à portée). */
    public static boolean isBroadcast(InterventionRequest request) {
        return request.getBroadcastStartedAt() != null
                || (request.getNotifiedProviders() != null && !request.getNotifiedProviders().isEmpty());
    }

    /**
     * Distance (km, arrondie au dixième) entre un prestataire et une résidence, null si une position manque.
     * Position du prestataire : GPS récent, sinon zone de référence du profil.
     */
    public Double distanceKm(ProviderProfile profile, Residence residence) {
        if (profile == null || residence == null || residence.getLatitude() == null || residence.getLongitude() == null) {
            return null;
        }
        return providerPosition(profile)
                .map(pos -> Math.round(geolocationService.calculateDistance(
                        residence.getLatitude().doubleValue(), residence.getLongitude().doubleValue(),
                        pos[0], pos[1]) * 10.0) / 10.0)
                .orElse(null);
    }

    /** Position retenue pour un prestataire : {latitude, longitude}. */
    public Optional<double[]> providerPosition(ProviderProfile profile) {
        LocalDateTime freshness = LocalDateTime.now().minusMinutes(gpsFreshnessMinutes);
        boolean gpsValid = profile.getGpsLatitude() != null && profile.getGpsLongitude() != null
                && profile.getGpsUpdatedAt() != null && profile.getGpsUpdatedAt().isAfter(freshness);
        if (gpsValid) {
            return Optional.of(new double[]{profile.getGpsLatitude().doubleValue(), profile.getGpsLongitude().doubleValue()});
        }
        if (profile.getLatitude() != null && profile.getLongitude() != null) {
            return Optional.of(new double[]{profile.getLatitude().doubleValue(), profile.getLongitude().doubleValue()});
        }
        return Optional.empty();
    }

    // =========================================================================
    // JOB : ÉLARGISSEMENT PAR PALIERS
    // =========================================================================

    @Scheduled(fixedDelayString = "${app.travaux.broadcast.job-delay-ms:60000}", initialDelay = 45_000)
    public void expandDueBroadcasts() {
        if (!jobEnabled) return;
        LocalDateTime now = LocalDateTime.now();
        List<Long> due = interventionRepository.findBroadcastIdsDue(
                OPEN_FOR_QUOTES, now.minusMinutes(stepMinutes), now.minusHours(maxAgeHours));
        for (Long id : due) {
            try {
                // Une transaction par demande : un échec n'empêche pas les autres d'avancer
                transactionTemplate.executeWithoutResult(status -> expandOne(id));
            } catch (Exception e) {
                log.error("Élargissement de la diffusion de la demande {} en échec : {}", id, e.getMessage());
            }
        }
    }

    private void expandOne(Long id) {
        // Verrou + nouvelle vérification : deux instances de l'application ne traitent jamais le même palier
        InterventionRequest request = interventionRepository.findByIdForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (request == null || request.getSelectedProvider() != null
                || !OPEN_FOR_QUOTES.contains(request.getStatus())
                || request.getLastBroadcastAt() == null
                || request.getLastBroadcastAt().isAfter(now.minusMinutes(stepMinutes))) {
            return;
        }
        long quotes = quoteRepository.countByInterventionRequestId(id);
        double current = request.getBroadcastRadiusKm() != null ? request.getBroadcastRadiusKm() : initialRadiusKm();
        double next = nextRadius(current);
        boolean enoughQuotes = stopAfterQuotes > 0 && quotes >= stopAfterQuotes;
        // Assez de devis : on n'élargit plus, mais on continue d'inclure les prestataires devenus
        // éligibles dans le rayon déjà atteint (rattrapage)
        double radius = enoughQuotes ? current : next;

        int notified = notifyEligibleProvidersWithin(request, radius);
        request.setBroadcastRadiusKm(radius);
        request.setLastBroadcastAt(now);
        interventionRepository.save(request);
        if (notified > 0 || radius > current) {
            log.info("Demande {} : diffusion à {} km, {} nouveau(x) prestataire(s), {} devis reçu(s)",
                    id, radius, notified, quotes);
        }
    }

    // =========================================================================
    // INTERNE
    // =========================================================================

    private int notifyEligibleProvidersWithin(InterventionRequest request, double radiusKm) {
        if (request.getSpecialty() == null) {
            return 0;
        }
        Residence residence = request.getResidence();
        Set<Long> alreadyNotified = new HashSet<>();
        request.getNotifiedProviders().forEach(u -> alreadyNotified.add(u.getId()));

        List<ProviderProfile> candidates = providerProfileRepository.findActiveProvidersBySpecialty(request.getSpecialty().getId());
        int added = 0;
        for (ProviderProfile profile : candidates) {
            User provider = profile.getUser();
            if (provider == null || alreadyNotified.contains(provider.getId())) continue;

            Double distance = distanceKm(profile, residence);
            if (distance == null || distance > radiusKm) continue;
            if (!hasActiveSubscription(provider)) continue;
            if (!eligibilityService.isEligible(provider.getId())) continue;

            request.getNotifiedProviders().add(provider);
            alreadyNotified.add(provider.getId());
            added++;
            notifyProvider(provider, request, residence, distance);
        }
        return added;
    }

    private boolean hasActiveSubscription(User provider) {
        // Abonnements suspendus : tout prestataire de la bonne spécialité peut être notifié
        if (!subscriptionPolicy.isEnabled()) return true;
        return providerSubscriptionRepository.findFirstByProviderIdOrderByEndDateDesc(provider.getId())
                .map(ProviderSubscription::isCurrentlyActive)
                .orElse(false);
    }

    private void notifyProvider(User provider, InterventionRequest request, Residence residence, double distanceKm) {
        if (!provider.isNotificationsEnabled()) return;
        Long providerId = provider.getId();
        String email = provider.getEmail();
        String firstName = provider.getFirstName();
        String title = request.getTitle();
        String residenceName = residence.getName();
        String distance = String.format(Locale.FRANCE, "%.1f", distanceKm);
        AfterCommit.run(() -> notificationService.sendPush(providerId, "Nouvelle demande de travaux près de vous",
                "À " + distance + " km : " + title));
        AfterCommit.run(() -> emailService.sendInterventionNotification(email, firstName, title, residenceName));
    }

    private double nextRadius(double current) {
        for (double r : radiiKm) {
            if (r > current + 1e-9) return r;
        }
        return maxRadiusKm();
    }

    static List<Double> parseRadii(String value) {
        List<Double> radii = new ArrayList<>();
        if (value != null) {
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Double::parseDouble)
                    .filter(r -> r > 0)
                    .sorted()
                    .distinct()
                    .forEach(radii::add);
        }
        if (radii.isEmpty()) {
            radii.addAll(List.of(5.0, 10.0, 15.0, 20.0, 30.0));
        }
        return List.copyOf(radii);
    }
}
