package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.intervention.CoOwnerQuoteCardDTO;
import com.example.solimus.entities.EstimatedDelay;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Quote;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.ProviderProfileRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.QuoteRepository;
import com.example.solimus.repositories.SyndicCoOwnerRelationRepository;
import com.example.solimus.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

// GET /api/owner/travaux/interventions/{id}/quotes — liste des devis côté copropriétaire
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerQuotesListingTest {

    @Mock private UserRepository userRepository;
    @Mock private InterventionRequestRepository interventionRequestRepository;
    @Mock private QuoteRepository quoteRepository;
    @Mock private ProviderProfileRepository providerProfileRepository;
    @Mock private ProviderSubscriptionRepository providerSubscriptionRepository;
    @Mock private SyndicCoOwnerRelationRepository coOwnerRelationRepository;

    @InjectMocks private ownerTravauxServiceImpl service;

    private User owner;
    private InterventionRequest request;

    @BeforeEach
    void setUp() {
        owner = user(73L);
        owner.setEmail("copro@example.test");
        Residence residence = new Residence();
        residence.setId(1L);

        request = new InterventionRequest();
        request.setId(10L);
        request.setOwner(owner);
        request.setResidence(residence);
        request.setManagementMode(InterventionManagementMode.OWNER);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("copro@example.test", null, List.of()));
        when(userRepository.findByEmail("copro@example.test")).thenReturn(Optional.of(owner));
        when(interventionRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(providerProfileRepository.findByUser(any())).thenReturn(Optional.empty()); // profil absent toléré
        when(providerSubscriptionRepository.findFirstByProviderIdAndStatus(anyLong(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("P" + id);
        u.setLastName("X");
        return u;
    }

    private Quote quote(long id, String amount, QuoteStatus status, int days) {
        Quote q = new Quote();
        q.setId(id);
        q.setProvider(user(100 + id));
        q.setTotalAmount(new BigDecimal(amount));
        q.setStatus(status);
        EstimatedDelay d = new EstimatedDelay();
        d.setDays(days);
        q.setEstimatedDelay(d);
        return q;
    }

    @Test
    void severalSentQuotes_noLonger500_sortedWithRecommendation() {
        when(quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request)).thenReturn(List.of(
                quote(1, "50000", QuoteStatus.SENT, 3),
                quote(2, "120000", QuoteStatus.SENT, 45), // délai > 30 j : score borné, pas négatif
                quote(3, "80000", QuoteStatus.DRAFT, 2)));  // brouillon : jamais visible

        Page<CoOwnerQuoteCardDTO> page = service.getQuotesByIntervention(10L, 0, 10);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getId()).isEqualTo(1L); // meilleur score en premier
        assertThat(page.getContent().get(0).isBestOffer()).isTrue();
        assertThat(page.getContent().get(1).isBestOffer()).isFalse();
        assertThat(page.getContent().get(1).getScoreFinal()).isGreaterThanOrEqualTo(0.0);
        assertThat(page.getContent().get(0).getStatusLabel()).isEqualTo("En attente");
    }

    @Test
    void afterValidation_acceptedQuoteStillListedFirst_withoutRecommendation() {
        when(quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request)).thenReturn(List.of(
                quote(1, "50000", QuoteStatus.REJECTED, 3),
                quote(2, "120000", QuoteStatus.ACCEPTED, 5)));

        Page<CoOwnerQuoteCardDTO> page = service.getQuotesByIntervention(10L, 0, 10);

        assertThat(page.getTotalElements()).isEqualTo(2); // avant : liste vide
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(page.getContent()).noneMatch(CoOwnerQuoteCardDTO::isBestOffer);
    }

    @Test
    void pageBeyondTotal_returnsEmptyPage_not500() {
        when(quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request))
                .thenReturn(List.of(quote(1, "50000", QuoteStatus.SENT, 3)));
        assertThat(service.getQuotesByIntervention(10L, 5, 10).getContent()).isEmpty();
    }

    @Test
    void privateWorks_ofAnotherOwner_areForbidden_evenInSameResidence() {
        request.setOwner(user(999L));
        when(coOwnerRelationRepository.existsByResidenceIdAndCoOwnerId(1L, 73L)).thenReturn(true);
        assertThatThrownBy(() -> service.getQuotesByIntervention(10L, 0, 10)).isInstanceOf(ForbiddenException.class);

        // Parties communes gérées par le syndic : visibles des copropriétaires de la résidence
        request.setManagementMode(InterventionManagementMode.SYNDIC);
        when(quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request)).thenReturn(List.of());
        assertThat(service.getQuotesByIntervention(10L, 0, 10).getContent()).isEmpty();
    }
}
