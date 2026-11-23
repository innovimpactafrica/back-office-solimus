package com.example.solimus.security;

import com.example.solimus.entities.User;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.shared.SubscriptionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Abonnements suspendus : syndic sans abonnement actif = toutes les fonctionnalités, aucune limite. */
class SubscriptionSuspensionTest {

    private final SyndicSubscriptionRepository subscriptions = mock(SyndicSubscriptionRepository.class);
    private final UserRepository users = mock(UserRepository.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsSyndicWithExpiredSubscription() {
        User syndic = new User();
        syndic.setId(65L);
        when(users.findByEmail("syndic@example.test")).thenReturn(Optional.of(syndic));
        when(subscriptions.findFirstBySyndicIdAndStatus(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("syndic@example.test", null, List.of()));
    }

    @Test
    void suspended_everyFeatureIsOpen_evenWithoutActiveSubscription() {
        loginAsSyndicWithExpiredSubscription();
        PlanFeatureGuard guard = new PlanFeatureGuard(subscriptions, users, new SubscriptionPolicy(false));

        assertThat(guard.hasFeature("CHARGE_MANAGEMENT")).isTrue();
        assertThat(guard.hasFeature("WALLET_MANAGEMENT")).isTrue();
    }

    @Test
    void enabled_expiredSubscriptionStillBlocks() {
        loginAsSyndicWithExpiredSubscription();
        PlanFeatureGuard guard = new PlanFeatureGuard(subscriptions, users, new SubscriptionPolicy(true));

        assertThat(guard.hasFeature("CHARGE_MANAGEMENT")).isFalse();
    }

    @Test
    void suspended_noResidenceOrApartmentLimit() {
        User syndic = new User();
        syndic.setId(65L);
        PlanLimitGuard guard = new PlanLimitGuard(subscriptions, mock(ResidenceRepository.class),
                mock(PropertyRepository.class), new SubscriptionPolicy(false));

        assertThatCode(() -> guard.assertCanAddResidence(syndic)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertCanAddApartments(syndic, 500)).doesNotThrowAnyException();
    }

    @Test
    void suspended_paidSubscriptionEndpointsAreRefused() {
        assertThatThrownBy(() -> new SubscriptionPolicy(false).assertEnabled()).isInstanceOf(BadRequestException.class);
        assertThatCode(() -> new SubscriptionPolicy(true).assertEnabled()).doesNotThrowAnyException();
    }
}
