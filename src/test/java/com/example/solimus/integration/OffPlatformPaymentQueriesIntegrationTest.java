package com.example.solimus.integration;

import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.ProofStatus;
import com.example.solimus.repositories.*;
import com.example.solimus.services.provider.request.ProviderRequestServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exécute réellement, sur une MySQL JETABLE, les requêtes ajoutées ou modifiées pour le paiement hors
 * plateforme et la diffusion progressive (SQL natif, JPQL, tri par distance), après mise à jour du schéma
 * par Hibernate. Ignoré sans -Dit.db.url ; jamais contre une vraie base.
 * <pre>
 * mvn test -Dtest=OffPlatformPaymentQueriesIntegrationTest -Dit.db.url=jdbc:mysql://127.0.0.1:33998/fake -Dit.db.user=root -Dit.db.password=fake
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.db.url", matches = "jdbc:mysql://(127\\.0\\.0\\.1|localhost)[:/].*")
@ActiveProfiles("docker")
@SpringBootTest(properties = {
        "spring.datasource.url=${it.db.url}",
        "spring.datasource.username=${it.db.user}",
        "spring.datasource.password=${it.db.password}",
        "DB_HOST=127.0.0.1", "DB_NAME=fake", "DB_USER=fake", "DB_PASSWORD=fake",
        "JWT_SECRET=fake-jwt-secret-fake-jwt-secret-fake-0123",
        "spring.main.lazy-initialization=false",
        "app.travaux.broadcast.job-enabled=false"
})
class OffPlatformPaymentQueriesIntegrationTest {

    @Autowired private ChargeCallItemRepository chargeCallItemRepository;
    @Autowired private ChargePaymentProofRepository chargeProofRepository;
    @Autowired private InterventionPaymentProofRepository interventionProofRepository;
    @Autowired private InterventionRequestRepository interventionRequestRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void schemaIsUpdatedWithVarcharStatusColumns() {
        assertThat(columnType("charge_call_items", "proof_status")).isEqualTo("varchar(30)");
        assertThat(columnType("exceptional_call_items", "proof_status")).isEqualTo("varchar(30)");
        assertThat(columnType("charge_payment_proofs", "status")).isEqualTo("varchar(30)");
        assertThat(columnType("intervention_payment_proofs", "payment_type")).isEqualTo("varchar(20)");
        assertThat(columnType("charge_call_payments", "off_platform")).isNotNull();
        assertThat(columnType("payments", "off_platform")).isNotNull();
        assertThat(columnType("intervention_requests", "broadcast_radius_km")).isNotNull();
    }

    @Test
    @Transactional(readOnly = true)
    void myChargesNativeQueryAcceptsThePendingValidationStatus() {
        PageRequest page = PageRequest.of(0, 10);
        for (String status : new String[]{null, "PENDING", "PENDING_VALIDATION", "PAID"}) {
            assertThat(chargeCallItemRepository.findMyChargesUnion(-1L, null, null, status, null, page)).isEmpty();
            assertThat(chargeCallItemRepository.sumMyChargesSummary(-1L, null, null, status, "x")).hasSize(1);
        }
    }

    @Test
    @Transactional(readOnly = true)
    void proofSearchesRun() {
        PageRequest page = PageRequest.of(0, 10);
        assertThat(chargeProofRepository.searchForSyndic(-1L, ProofStatus.PENDING_VALIDATION, 1L, ChargeType.REGULAR, "x", page)).isEmpty();
        assertThat(chargeProofRepository.searchForSyndic(-1L, null, null, null, null, page)).isEmpty();
        assertThat(chargeProofRepository.countBySyndicAndStatus(-1L, ProofStatus.PENDING_VALIDATION)).isZero();
        assertThat(interventionProofRepository.searchForProvider(-1L, ProofStatus.VALIDATED, "x", page)).isEmpty();
        assertThat(interventionProofRepository.countByProviderIdAndStatus(-1L, ProofStatus.PENDING_VALIDATION)).isZero();
    }

    @Test
    @Transactional // écriture (annulée en fin de test) : SELECT … FOR UPDATE
    void lockQueriesRun() {
        assertThat(chargeProofRepository.findByIdForUpdate(-1L)).isEmpty();
        assertThat(interventionProofRepository.findByIdForUpdate(-1L)).isEmpty();
        assertThat(chargeCallItemRepository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    @Transactional(readOnly = true)
    void broadcastAndDistanceSortQueriesRun() {
        assertThat(interventionRequestRepository.findBroadcastIdsDue(
                List.of(InterventionStatus.PENDING, InterventionStatus.SYNDIC_ASSIGNED),
                LocalDateTime.now(), LocalDateTime.now().minusHours(72))).isNotNull();
        assertThat(quoteRepository.countByInterventionRequestId(-1L)).isZero();

        User anyUser = userRepository.findAll().stream().findFirst().orElseThrow();
        Sort byDistance = ReflectionTestUtils.invokeMethod(ProviderRequestServiceImpl.class, "buildSort",
                com.example.solimus.enums.ProviderRequestSort.DISTANCE, Optional.of(new double[]{14.67, -17.43}));
        PageRequest page = PageRequest.of(0, 10, byDistance);
        assertThat(interventionRequestRepository.findAllNotifiedRequests(anyUser, null, page)).isEmpty();
        assertThat(interventionRequestRepository.findPendingQuoteRequests(anyUser, "x", page)).isEmpty();
        assertThat(interventionRequestRepository.findQuoteSentRequests(anyUser, null, page)).isEmpty();
        assertThat(interventionRequestRepository.findRejectedRequests(anyUser, null, page)).isEmpty();
    }

    private String columnType(String table, String column) {
        List<String> types = jdbc.queryForList("SELECT column_type FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?", String.class, table, column);
        return types.isEmpty() ? null : types.get(0);
    }
}
