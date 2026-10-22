package com.example.solimus.services.payment;

import com.example.solimus.dtos.payment.MobileMoneyPaymentRequestDTO;
import com.example.solimus.dtos.payment.MobileMoneyPaymentResponseDTO;
import com.example.solimus.entities.MobileMoneyTransaction;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.MobileMoneyOperator;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.PaymentGatewayException;
import com.example.solimus.repositories.ChargeCallPaymentRepository;
import com.example.solimus.repositories.ExceptionalCallPaymentRepository;
import com.example.solimus.repositories.MobileMoneyTransactionRepository;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Flux Wave / Orange Money contre un faux serveur GU local (aucun appel réseau réel, faux identifiants)
class MobileMoneyPaymentServiceImplTest {

    private static final String REF = "SYR-ABC123DEF456";
    private static final String REALM = "realm-test";
    private static final String NONCE = "nonce-test";

    // Recalcule la réponse Digest attendue (RFC 2617, qop=auth) avec les identifiants de test
    private static boolean validDigest(String header, String method, String uri) {
        Map<String, String> p = DigestAuth.parse(header);
        String ha1 = DigestAuth.md5("digest_user:" + REALM + ":digest_pwd");
        String ha2 = DigestAuth.md5(method + ":" + uri);
        String expected = DigestAuth.md5(ha1 + ":" + NONCE + ":" + p.get("nc") + ":" + p.get("cnonce") + ":auth:" + ha2);
        return "digest_user".equals(p.get("username")) && uri.equals(p.get("uri")) && expected.equals(p.get("response"));
    }

    private HttpServer gu;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastUri = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> lastBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody;
    private volatile String guStatus = "INITIATED";
    private final java.util.concurrent.atomic.AtomicInteger statusCalls = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<String, MobileMoneyTransaction> tracked = new java.util.concurrent.ConcurrentHashMap<>();

    private SyndicSubscriptionRepository syndicSubscriptionRepository;
    private UserRepository userRepository;
    private PaymentConfirmationService confirmation;
    private MobileMoneyPaymentServiceImpl service;
    private User syndic;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        gu = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gu.createContext("/", exchange -> {
            // Comme le vrai GU : 401 + défi Digest tant que l'en-tête Authorization n'est pas valide
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !validDigest(auth, exchange.getRequestMethod(), exchange.getRequestURI().toString())) {
                exchange.getResponseHeaders().add("WWW-Authenticate",
                        "Digest realm=\"" + REALM + "\", domain=\"/\", nonce=\"" + NONCE + "\", algorithm=MD5, qop=\"auth\"");
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                // Statut GU : GET /v1/{agence}/transaction/{idFromClient}
                byte[] out = (guStatus == null
                        ? "{\"status\":404,\"detailMessage\":\"No transaction found with this ID\"}"
                        : "{\"idFromClient\":\"AGENCE_TEST_" + REF + "\",\"status\":\"" + guStatus + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                statusCalls.incrementAndGet();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(guStatus == null ? 404 : 200, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
                return;
            }
            lastMethod.set(exchange.getRequestMethod());
            lastUri.set(exchange.getRequestURI().toString());
            lastBody.set(new ObjectMapper().readValue(exchange.getRequestBody(), Map.class));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        gu.start();

        syndicSubscriptionRepository = mock(SyndicSubscriptionRepository.class);
        userRepository = mock(UserRepository.class);
        confirmation = mock(PaymentConfirmationService.class);
        when(confirmation.routeCallbackByPrefix(anyString(), anyBoolean()))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "message", "ok")));

        MobileMoneyTransactionRepository trackingRepo = mock(MobileMoneyTransactionRepository.class);
        when(trackingRepo.findByReference(anyString())).thenAnswer(inv -> Optional.ofNullable(tracked.get((String) inv.getArgument(0))));
        when(trackingRepo.findByReferenceForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(tracked.get((String) inv.getArgument(0))));
        when(trackingRepo.save(any())).thenAnswer(inv -> {
            MobileMoneyTransaction t = inv.getArgument(0);
            tracked.put(t.getReference(), t);
            return t;
        });

        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service = new MobileMoneyPaymentServiceImpl(syndicSubscriptionRepository,
                mock(ProviderSubscriptionRepository.class), mock(ChargeCallPaymentRepository.class),
                mock(ExceptionalCallPaymentRepository.class), mock(PaymentRepository.class),
                userRepository, confirmation, trackingRepo, tm);
        ReflectionTestUtils.setField(service, "apiBaseUrl", "http://127.0.0.1:" + gu.getAddress().getPort() + "/v1");
        ReflectionTestUtils.setField(service, "agencyCode", "AGENCE_TEST");
        ReflectionTestUtils.setField(service, "loginAgent", "login_test");
        ReflectionTestUtils.setField(service, "passwordAgent", "pwd_test");
        ReflectionTestUtils.setField(service, "digestUsername", "digest_user");
        ReflectionTestUtils.setField(service, "digestPassword", "digest_pwd");
        ReflectionTestUtils.setField(service, "httpMethod", "PUT");
        ReflectionTestUtils.setField(service, "partnerName", "Solimus");
        ReflectionTestUtils.setField(service, "backendBaseUrl", "https://api.solimus.sn");
        ReflectionTestUtils.setField(service, "syndicReturnUrl", "https://syndic.solimus.sn/app/parametres");
        ReflectionTestUtils.setField(service, "adminReturnUrl", "https://bo.solimus.sn");
        ReflectionTestUtils.setField(service, "adminSyndicCreatedReturnUrl", "https://bo.solimus.sn/syndics");
        ReflectionTestUtils.setField(service, "signingKey", "cle-de-test");

        syndic = new User();
        syndic.setId(65L);
        syndic.setEmail("syndic@example.test");
        syndic.setFirstName("Awa");
        syndic.setLastName("Diop");
        syndic.setPhone("+221 77 123 45 67");

        SyndicSubscription sub = new SyndicSubscription();
        sub.setTransactionRef(REF);
        sub.setAmountPaid(new BigDecimal("15000.00"));
        sub.setPaymentStatus(PaymentStatus.PENDING);
        sub.setInitiatedBy(syndic);
        sub.setSyndic(syndic);
        when(syndicSubscriptionRepository.findByTransactionRef(REF)).thenReturn(Optional.of(sub));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("syndic@example.test", null, List.of()));
        when(userRepository.findByEmail("syndic@example.test")).thenReturn(Optional.of(syndic));

        responseBody = """
                {"idFromClient":"SYR-ABC123DEF456","idFromGU":"1792406226644","amount":15000,"fees":22.5,
                 "serviceCode":"SNPAIEMENTWAVE","recipientNumber":"771234567","dateTime":1792406226644,
                 "status":"INITIATED","numTransaction":"1792406226644",
                 "payment_url":"https://pay.wave.com/c/cos-test?a=15000&c=XOF"}""";
    }

    @AfterEach
    void tearDown() {
        gu.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void wave_sendsExpectedRequest_andReturnsPaymentUrl() {
        MobileMoneyPaymentResponseDTO res = service.initiatePayment(REF, MobileMoneyOperator.WAVE, null);

        assertThat(lastMethod.get()).isEqualTo("PUT");
        assertThat(lastUri.get()).startsWith("/v1/AGENCE_TEST/transaction?loginAgent=login_test&passwordAgent=pwd_test");
        Map<String, Object> body = lastBody.get();
        assertThat(body.get("serviceCode")).isEqualTo("SNPAIEMENTWAVE");
        assertThat(body.get("idFromClient")).isEqualTo(REF);
        assertThat(((Number) body.get("amount")).longValue()).isEqualTo(15000L); // prix de la formule (serveur)
        assertThat(body.get("recipientNumber")).isEqualTo("771234567");          // +221 retiré
        assertThat(body.get("callback")).isEqualTo("https://api.solimus.sn/api/payments/intouch/gutouch/callback");
        Map<String, Object> infos = (Map<String, Object>) body.get("additionnalInfos");
        assertThat(infos.get("recipientEmail")).isEqualTo("syndic@example.test");
        assertThat(infos.get("currency")).isEqualTo("XOF");
        // Parcours web syndic : retour direct sur la page de l'application (jamais localhost)
        assertThat((String) infos.get("return_url"))
                .startsWith("https://syndic.solimus.sn/app/parametres?paymentStatus=success&ref=" + REF + "&sig=");
        assertThat((String) infos.get("cancel_url"))
                .startsWith("https://syndic.solimus.sn/app/parametres?paymentStatus=cancelled&ref=" + REF + "&sig=");

        assertThat(res.getPaymentUrl()).isEqualTo("https://pay.wave.com/c/cos-test?a=15000&c=XOF");
        assertThat(res.getServiceCode()).isEqualTo("SNPAIEMENTWAVE");
        assertThat(res.getAmount()).isEqualByComparingTo("15000");
        assertThat(res.getFees()).isEqualByComparingTo("22.5");
        assertThat(res.getStatus()).isEqualTo("INITIATED");
        verify(confirmation, never()).routeCallbackByPrefix(anyString(), anyBoolean());
    }

    @Test
    void wrongDigestCredentials_give502WithClearMessage() {
        ReflectionTestUtils.setField(service, "digestPassword", "mauvais");
        assertThatThrownBy(() -> service.initiatePayment(REF, MobileMoneyOperator.WAVE, null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("GUTOUCH_DIGEST_USERNAME");
        verify(confirmation).routeCallbackByPrefix(REF, false);
    }

    @Test
    void guDuplicateRefusal_http300_isAnErrorNotASuccess() {
        // Réponse réelle observée : HTTP 300 + statut 300, sans payment_url
        responseStatus = 300;
        responseBody = "{\"status\":300,\"detailMessage\":\"Une operation similaire a ete envoyee il y a moins de 1 minutes\","
                + "\"stackTrace\":[],\"suppressedExceptions\":[]}";
        assertThatThrownBy(() -> service.initiatePayment(REF, MobileMoneyOperator.WAVE, null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Une operation similaire a ete envoyee")
                .hasMessageContaining("patientez une minute");
        verify(confirmation).routeCallbackByPrefix(REF, false); // tentative libérée pour relancer
    }

    @Test
    void waveWithoutPaymentUrl_isRefused() {
        responseBody = "{\"status\":\"INITIATED\",\"idFromGU\":\"1\"}";
        assertThatThrownBy(() -> service.initiatePayment(REF, MobileMoneyOperator.WAVE, null))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileUrls_areKept_andUsedAfterGuVerification() {
        MobileMoneyPaymentRequestDTO request = new MobileMoneyPaymentRequestDTO();
        request.setReturnUrl("solimus://payment/success");
        request.setCancelUrl("https://app.solimus.sn/paiement/annule");

        MobileMoneyPaymentResponseDTO res = service.initiatePayment(REF, MobileMoneyOperator.WAVE, request);

        // GU reçoit toujours le retour public signé du backend (jamais l'URL du mobile, jamais localhost)
        Map<String, Object> infos = (Map<String, Object>) lastBody.get().get("additionnalInfos");
        String guReturn = (String) infos.get("return_url");
        assertThat(guReturn).startsWith("https://api.solimus.sn/api/payments/intouch/gutouch/return?ref=" + REF + "&sig=");
        assertThat((String) infos.get("cancel_url")).startsWith("https://api.solimus.sn/api/payments/intouch/gutouch/cancel?ref=");
        // La réponse indique où l'app sera renvoyée
        assertThat(res.getReturnUrl()).isEqualTo("solimus://payment/success?paymentStatus=success&ref=" + REF);
        assertThat(res.getCancelUrl()).isEqualTo("https://app.solimus.sn/paiement/annule?paymentStatus=failed&ref=" + REF);

        // Retour : statut vérifié chez GU, paiement appliqué, puis redirection vers l'URL du mobile
        guStatus = "SUCCEED";
        String sig = guReturn.substring(guReturn.indexOf("&sig=") + 5);
        assertThat(service.handleReturn(REF, sig)).isEqualTo("solimus://payment/success?paymentStatus=success&ref=" + REF);
        verify(confirmation).routeCallbackByPrefix(REF, true);
    }

    @Test
    void dangerousMobileUrl_isRejected_beforeAnyGuCall() {
        MobileMoneyPaymentRequestDTO request = new MobileMoneyPaymentRequestDTO();
        request.setReturnUrl("javascript:alert(1)");
        assertThatThrownBy(() -> service.initiatePayment(REF, MobileMoneyOperator.WAVE, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("return_url");
        assertThat(lastBody.get()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminSyndicCreation_returnsToSyndicsList() {
        SyndicSubscription sub = new SyndicSubscription();
        sub.setTransactionRef("SYN-ABC123DEF456");
        sub.setAmountPaid(new BigDecimal("15000"));
        sub.setPaymentStatus(PaymentStatus.PENDING);
        sub.setInitiatedBy(syndic); // payeur courant du test
        when(syndicSubscriptionRepository.findByTransactionRef("SYN-ABC123DEF456")).thenReturn(Optional.of(sub));

        MobileMoneyPaymentResponseDTO res = service.initiatePayment("SYN-ABC123DEF456", MobileMoneyOperator.WAVE, null);

        Map<String, Object> infos = (Map<String, Object>) lastBody.get().get("additionnalInfos");
        assertThat((String) infos.get("return_url"))
                .startsWith("https://bo.solimus.sn/syndics?paymentStatus=success&ref=SYN-ABC123DEF456&sig=");
        assertThat(res.getReturnUrl()).isEqualTo("https://bo.solimus.sn/syndics?paymentStatus=success&ref=SYN-ABC123DEF456");
    }

    @Test
    void orangeMoney_usesMerchantServiceCode() {
        service.initiatePayment(REF, MobileMoneyOperator.ORANGE_MONEY, null);
        assertThat(lastBody.get().get("serviceCode")).isEqualTo("PAIEMENTMARCHANDOM");
    }

    @Test
    void clientAmount_isOnlyAControl() {
        assertThatThrownBy(() -> service.initiatePayment(REF, MobileMoneyOperator.WAVE,
                MobileMoneyPaymentRequestDTO.builder().amount(new BigDecimal("100")).build()))
                .isInstanceOf(BadRequestException.class);
        assertThat(lastBody.get()).isNull(); // aucun appel à GU
    }

    @Test
    void onlyThePayerCanPay() {
        User other = new User();
        other.setId(1L);
        when(userRepository.findByEmail("syndic@example.test")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.initiatePayment(REF, MobileMoneyOperator.WAVE, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void gatewayError_marksAttemptFailed_and502() {
        responseStatus = 500;
        responseBody = "{\"message\":\"erreur\"}";
        assertThatThrownBy(() -> service.initiatePayment(REF, MobileMoneyOperator.WAVE, null))
                .isInstanceOf(PaymentGatewayException.class);
        verify(confirmation).routeCallbackByPrefix(REF, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void appConfirmation_checksGuStatus_thenMarksPaidOnce() {
        service.initiatePayment(REF, MobileMoneyOperator.WAVE, null);
        assertThat(tracked.get(REF).getStatus()).isEqualTo("INITIATED");
        String returnUrl = (String) ((Map<String, Object>) lastBody.get().get("additionnalInfos")).get("return_url");
        String sig = returnUrl.substring(returnUrl.indexOf("&sig=") + 5);

        // GU : pas encore payé → rien n'est appliqué
        Map<String, Object> pending = service.confirmFromApp(REF, sig);
        assertThat(pending.get("paid")).isEqualTo(false);
        verify(confirmation, never()).routeCallbackByPrefix(REF, true);

        // GU : SUCCEED → statut payé appliqué, une seule fois même si on rappelle
        guStatus = "SUCCEED";
        assertThat(service.confirmFromApp(REF, sig).get("paid")).isEqualTo(true);
        service.confirmFromApp(REF, sig);
        verify(confirmation, org.mockito.Mockito.times(1)).routeCallbackByPrefix(REF, true);
        assertThat(tracked.get(REF).getStatus()).isEqualTo("SUCCEED");
        assertThat(tracked.get(REF).getFinalizedAt()).isNotNull();

        assertThatThrownBy(() -> service.confirmFromApp(REF, "signature-forgee"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void scheduler_reconcilesPaidTransaction_withoutFrontOrCallback() {
        service.initiatePayment(REF, MobileMoneyOperator.WAVE, null);
        guStatus = "SUCCEED";
        org.mockito.Mockito.doReturn(List.copyOf(tracked.values()))
                .when((MobileMoneyTransactionRepository) ReflectionTestUtils.getField(service, "transactionRepository"))
                .findByFinalizedAtIsNullAndCreatedAtAfter(any());

        service.reconcilePendingTransactions();

        verify(confirmation).routeCallbackByPrefix(REF, true);
    }

    @Test
    void callback_neverTrustsPayload_andStripsAgencyPrefix() {
        // Payload annonce SUCCESSFUL mais GU dit INITIATED → rien
        service.handleCallback(Map.of("idFromClient", "AGENCE_TEST_" + REF, "status", "SUCCESSFUL"));
        verify(confirmation, never()).routeCallbackByPrefix(anyString(), anyBoolean());

        // Callback forgé "réussi" mais GU dit FAILED → échec appliqué, jamais "payé"
        guStatus = "FAILED";
        service.handleCallback(Map.of("idFromClient", "AGENCE_TEST_" + REF, "status", "SUCCESSFUL"));
        verify(confirmation).routeCallbackByPrefix(REF, false);
        verify(confirmation, never()).routeCallbackByPrefix(REF, true);
    }
}
