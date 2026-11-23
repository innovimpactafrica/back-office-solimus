package com.example.solimus.services.travaux;

import com.example.solimus.entities.*;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.provider.eligibility.ProviderEligibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterventionBroadcastServiceTest {

    // Résidence à Dakar Plateau ; 0,009° de latitude ≈ 1 km
    private static final double LAT = 14.6700;
    private static final double LON = -17.4300;

    private final InterventionRequestRepository interventionRepository = mock(InterventionRequestRepository.class);
    private final ProviderProfileRepository profileRepository = mock(ProviderProfileRepository.class);
    private final ProviderSubscriptionRepository subscriptionRepository = mock(ProviderSubscriptionRepository.class);
    private final QuoteRepository quoteRepository = mock(QuoteRepository.class);
    private final ProviderEligibilityService eligibility = mock(ProviderEligibilityService.class);
    private InterventionBroadcastService service;
    private InterventionRequest request;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new InterventionBroadcastService(interventionRepository, profileRepository, subscriptionRepository,
                new com.example.solimus.services.shared.SubscriptionPolicy(true),
                quoteRepository, eligibility, new GeolocationService(), mock(NotificationService.class),
                mock(EmailService.class), tm, "10, 5, 20", 10, 3, 72, 60, true);

        Residence residence = new Residence();
        residence.setName("Résidence Test");
        residence.setLatitude(BigDecimal.valueOf(LAT));
        residence.setLongitude(BigDecimal.valueOf(LON));
        Specialty specialty = new Specialty();
        specialty.setId(3L);
        request = new InterventionRequest();
        request.setId(1L);
        request.setTitle("Fuite d'eau");
        request.setStatus(InterventionStatus.PENDING);
        request.setResidence(residence);
        request.setSpecialty(specialty);
        request.setNotifiedProviders(new ArrayList<>());

        ProviderProfile near = provider(101L, 2, true, true);       // 2 km, éligible
        ProviderProfile mid = provider(102L, 8, true, true);        // 8 km, éligible
        ProviderProfile poor = provider(103L, 1, true, false);      // 1 km, wallet insuffisant
        ProviderProfile noSub = provider(104L, 1, false, true);     // 1 km, sans abonnement
        ProviderProfile far = provider(105L, 25, true, true);       // 25 km, hors rayon max
        when(profileRepository.findActiveProvidersBySpecialty(3L)).thenReturn(List.of(near, mid, poor, noSub, far));
        when(interventionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
    }

    @Test
    void radiiAreSortedAndDeduplicated() {
        assertThat(InterventionBroadcastService.parseRadii("10, 5,5, 20")).containsExactly(5.0, 10.0, 20.0);
        assertThat(InterventionBroadcastService.parseRadii("")).containsExactly(5.0, 10.0, 15.0, 20.0, 30.0);
        assertThat(service.initialRadiusKm()).isEqualTo(5.0);
        assertThat(service.maxRadiusKm()).isEqualTo(20.0);
    }

    @Test
    void firstWaveOnlyReachesEligibleProvidersWithinFiveKm() {
        int notified = service.startBroadcast(request);

        assertThat(notified).isEqualTo(1);
        assertThat(ids(request)).containsExactly(101L);
        assertThat(request.getBroadcastRadiusKm()).isEqualTo(5.0);
        assertThat(request.getBroadcastStartedAt()).isNotNull();
        assertThat(service.startBroadcast(request)).isZero(); // déjà diffusée : sans effet
    }

    @Test
    void theRadiusWidensStepByStepUntilTheMaximum() {
        service.startBroadcast(request);
        when(interventionRepository.findBroadcastIdsDue(anyCollection(), any(), any())).thenReturn(List.of(1L));

        request.setLastBroadcastAt(LocalDateTime.now().minusMinutes(11));
        service.expandDueBroadcasts();
        assertThat(request.getBroadcastRadiusKm()).isEqualTo(10.0);
        assertThat(ids(request)).containsExactly(101L, 102L);

        request.setLastBroadcastAt(LocalDateTime.now().minusMinutes(11));
        service.expandDueBroadcasts();
        assertThat(request.getBroadcastRadiusKm()).isEqualTo(20.0);
        assertThat(ids(request)).containsExactly(101L, 102L); // 25 km : jamais atteint
    }

    @Test
    void noWideningBeforeTheStepDelayNorOnceAProviderIsChosen() {
        service.startBroadcast(request);
        when(interventionRepository.findBroadcastIdsDue(anyCollection(), any(), any())).thenReturn(List.of(1L));

        service.expandDueBroadcasts(); // dernier palier trop récent
        assertThat(request.getBroadcastRadiusKm()).isEqualTo(5.0);

        request.setLastBroadcastAt(LocalDateTime.now().minusMinutes(11));
        request.setSelectedProvider(new User());
        service.expandDueBroadcasts();
        assertThat(request.getBroadcastRadiusKm()).isEqualTo(5.0);
    }

    @Test
    void enoughQuotesStopsTheWidening() {
        service.startBroadcast(request);
        when(interventionRepository.findBroadcastIdsDue(anyCollection(), any(), any())).thenReturn(List.of(1L));
        when(quoteRepository.countByInterventionRequestId(1L)).thenReturn(3L);

        request.setLastBroadcastAt(LocalDateTime.now().minusMinutes(11));
        service.expandDueBroadcasts();
        assertThat(request.getBroadcastRadiusKm()).isEqualTo(5.0);
    }

    @Test
    void aResidenceWithoutGpsCannotBeBroadcast() {
        request.getResidence().setLatitude(null);
        assertThatThrownBy(() -> service.startBroadcast(request)).isInstanceOf(BadRequestException.class);
    }

    // ── utilitaires ──

    private ProviderProfile provider(Long userId, double kmNorth, boolean subscribed, boolean eligible) {
        User user = new User();
        user.setId(userId);
        user.setNotificationsEnabled(false);
        ProviderProfile profile = new ProviderProfile();
        profile.setUser(user);
        profile.setLatitude(BigDecimal.valueOf(LAT + kmNorth / 111.2));
        profile.setLongitude(BigDecimal.valueOf(LON));
        ProviderSubscription subscription = mock(ProviderSubscription.class);
        when(subscription.isCurrentlyActive()).thenReturn(subscribed);
        when(subscriptionRepository.findFirstByProviderIdOrderByEndDateDesc(userId)).thenReturn(Optional.of(subscription));
        when(eligibility.isEligible(userId)).thenReturn(eligible);
        return profile;
    }

    private static List<Long> ids(InterventionRequest request) {
        return request.getNotifiedProviders().stream().map(User::getId).toList();
    }
}
