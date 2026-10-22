package com.example.solimus.services.payment;

import com.example.solimus.dtos.payment.MobileMoneyPaymentRequestDTO;
import com.example.solimus.dtos.payment.MobileMoneyPaymentResponseDTO;
import com.example.solimus.entities.MobileMoneyTransaction;
import com.example.solimus.entities.User;
import com.example.solimus.enums.MobileMoneyOperator;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.PaymentGatewayException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ChargeCallPaymentRepository;
import com.example.solimus.repositories.ExceptionalCallPaymentRepository;
import com.example.solimus.repositories.MobileMoneyTransactionRepository;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class MobileMoneyPaymentServiceImpl implements MobileMoneyPaymentService {

    // Statuts finaux GU (GET /{agence}/transaction/{idFromClient}) : SUCCEED = payé, FAILED = échoué.
    // Variantes tolérées par prudence ; tout autre statut (INITIATED...) = paiement encore en cours.
    private static final Set<String> SUCCESS_STATUSES =
            Set.of("SUCCEED", "SUCCEEDED", "SUCCESSFUL", "SUCCESSFULL", "SUCCESS", "PAID", "COMPLETED");
    // Statuts GU d'une initiation acceptée (le paiement reste à valider par le client)
    private static final Set<String> INITIATED_STATUSES = Set.of("INITIATED", "PENDING", "SUCCESSFUL", "SUCCESS");
    private static final Set<String> FAILURE_STATUSES =
            Set.of("FAILED", "FAILLED", "FAILURE", "CANCELLED", "CANCELED", "ECHEC", "ÉCHEC", "ERROR", "EXPIRED", "REJECTED");

    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentConfirmationService paymentConfirmationService;
    private final MobileMoneyTransactionRepository transactionRepository;
    private final TransactionTemplate readOnlyTx;
    private final TransactionTemplate writeTx;
    private final RestClient restClient;

    // --- Accès API TouchPay (GU) : uniquement via variables d'environnement, jamais en dur ---
    @Value("${GUTOUCH_API_BASE_URL:https://api.gutouch.com/dist/api/touchpayapi/v1}")
    private String apiBaseUrl;
    @Value("${GUTOUCH_AGENCY_CODE:}")
    private String agencyCode;
    @Value("${GUTOUCH_LOGIN_AGENT:}")
    private String loginAgent;
    @Value("${GUTOUCH_PASSWORD_AGENT:}")
    private String passwordAgent;
    // Identifiants de l'authentification HTTP Digest exigée par l'API GU (distincts du caissier)
    @Value("${GUTOUCH_DIGEST_USERNAME:}")
    private String digestUsername;
    @Value("${GUTOUCH_DIGEST_PASSWORD:}")
    private String digestPassword;
    // Verbe HTTP de l'appel /transaction (PUT par défaut) — à aligner sur la collection Insomnia de GU
    @Value("${GUTOUCH_HTTP_METHOD:PUT}")
    private String httpMethod;
    @Value("${GUTOUCH_PARTNER_NAME:Solimus}")
    private String partnerName;

    // --- URLs : backend public (callback + retour), puis pages de l'application après paiement ---
    // URL PUBLIQUE du backend (callback GU, retour mobile). GU refuse les URLs localhost ("Request
    // invalid") : on ne retombe donc jamais sur app.backend.base-url, qui vaut localhost en local.
    @Value("${GUTOUCH_BACKEND_BASE_URL:https://api.solimus.sn}")
    private String backendBaseUrl;
    @Value("${GUTOUCH_SYNDIC_RETURN_URL:https://syndic.solimus.sn/app/parametres}")
    private String syndicReturnUrl;
    @Value("${GUTOUCH_ADMIN_RETURN_URL:https://bo.solimus.sn}")
    private String adminReturnUrl;
    // Création d'un syndic par l'admin (SYN-) : retour direct sur la liste des syndics
    @Value("${GUTOUCH_ADMIN_SYNDIC_CREATED_RETURN_URL:https://bo.solimus.sn/syndics}")
    private String adminSyndicCreatedReturnUrl;
    // Clé de signature des liens de retour (HMAC) — réutilise le secret JWT déjà configuré
    @Value("${jwt.secret}")
    private String signingKey;

    public MobileMoneyPaymentServiceImpl(SyndicSubscriptionRepository syndicSubscriptionRepository,
                                         ProviderSubscriptionRepository providerSubscriptionRepository,
                                         ChargeCallPaymentRepository chargeCallPaymentRepository,
                                         ExceptionalCallPaymentRepository exceptionalCallPaymentRepository,
                                         PaymentRepository paymentRepository,
                                         UserRepository userRepository,
                                         PaymentConfirmationService paymentConfirmationService,
                                         MobileMoneyTransactionRepository transactionRepository,
                                         PlatformTransactionManager transactionManager) {
        this.syndicSubscriptionRepository = syndicSubscriptionRepository;
        this.providerSubscriptionRepository = providerSubscriptionRepository;
        this.chargeCallPaymentRepository = chargeCallPaymentRepository;
        this.exceptionalCallPaymentRepository = exceptionalCallPaymentRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.paymentConfirmationService = paymentConfirmationService;
        this.transactionRepository = transactionRepository;
        this.writeTx = new TransactionTemplate(transactionManager);

        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // Où renvoyer l'utilisateur après paiement, selon qui paie (déduit du préfixe de référence)
    private enum Audience { SYNDIC_WEB, ADMIN_WEB, MOBILE }

    // Ce qu'il faut savoir d'une référence en attente pour la payer — copié hors transaction
    private record Payable(String reference, BigDecimal amount, Long payerId, String email,
                           String firstName, String lastName, String phone) {
    }

    // =========================================================================
    // INITIATION
    // =========================================================================

    @Override
    public MobileMoneyPaymentResponseDTO initiatePayment(String reference, MobileMoneyOperator operator,
                                                         MobileMoneyPaymentRequestDTO dto) {
        if (operator == null) {
            throw new BadRequestException("L'opérateur est obligatoire (WAVE ou ORANGE_MONEY)");
        }
        return switch (operator) {
            case WAVE -> createPaymentWave(reference, dto);
            case ORANGE_MONEY -> createPaymentOM(reference, dto);
        };
    }

    @Override
    public void preflight(String phoneNumber, String returnUrl, String cancelUrl) {
        assertGatewayConfigured();
        normalizePhone(phoneNumber != null ? phoneNumber : getCurrentUser().getPhone());
        validateClientUrl("return_url", returnUrl);
        validateClientUrl("cancel_url", cancelUrl);
    }

    @Override
    public MobileMoneyPaymentResponseDTO createPaymentWave(String reference, MobileMoneyPaymentRequestDTO dto) {
        return createPayment(reference, MobileMoneyOperator.WAVE, dto);
    }

    @Override
    public MobileMoneyPaymentResponseDTO createPaymentOM(String reference, MobileMoneyPaymentRequestDTO dto) {
        return createPayment(reference, MobileMoneyOperator.ORANGE_MONEY, dto);
    }

    // Pas de transaction ouverte pendant l'appel HTTP à GU : la lecture se fait dans une transaction
    // courte, la confirmation (succès / échec) dans la sienne via PaymentConfirmationService
    private MobileMoneyPaymentResponseDTO createPayment(String reference, MobileMoneyOperator operator,
                                                       MobileMoneyPaymentRequestDTO dto) {
        assertGatewayConfigured();
        MobileMoneyPaymentRequestDTO request = dto != null ? dto : new MobileMoneyPaymentRequestDTO();

        Payable payable = readOnlyTx.execute(status -> resolvePayable(reference));

        // Seul le payeur de cette référence peut la régler (l'admin pour SYN-/SYA-/PRA-, le syndic
        // pour SYR-, le copropriétaire pour CPY-/ECP-/PAY-/SOL-, le prestataire pour SUB-)
        User current = getCurrentUser();
        if (!current.getId().equals(payable.payerId())) {
            throw new ForbiddenException("Vous n'êtes pas le payeur de cette transaction");
        }

        // Le montant facturé est toujours celui du serveur ; celui du client n'est qu'un contrôle
        if (request.getAmount() != null && request.getAmount().compareTo(payable.amount()) != 0) {
            throw new BadRequestException("Montant incohérent : " + payable.amount() + " FCFA attendu pour " + reference);
        }

        String phone = normalizePhone(request.getPhoneNumber() != null ? request.getPhoneNumber() : payable.phone());
        String clientReturnUrl = validateClientUrl("return_url", request.getReturnUrl());
        String clientCancelUrl = validateClientUrl("cancel_url", request.getCancelUrl());
        boolean clientRedirect = clientReturnUrl != null || clientCancelUrl != null;
        long amount = toXof(payable.amount());

        Map<String, Object> additionalInfos = new LinkedHashMap<>();
        additionalInfos.put("recipientEmail", payable.email());
        additionalInfos.put("recipientFirstName", payable.firstName());
        additionalInfos.put("recipientLastName", payable.lastName());
        additionalInfos.put("destinataire", phone);
        additionalInfos.put("partner_name", partnerName);
        additionalInfos.put("return_url", returnUrl(reference, true, clientRedirect));
        additionalInfos.put("cancel_url", returnUrl(reference, false, clientRedirect));
        additionalInfos.put("currency", "XOF");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idFromClient", reference);
        body.put("additionnalInfos", additionalInfos); // orthographe imposée par l'API GU
        body.put("amount", amount);
        body.put("callback", backendBaseUrl + "/api/payments/intouch/gutouch/callback");
        body.put("recipientNumber", phone);
        body.put("serviceCode", operator.getServiceCode());

        Map<String, Object> response = callGateway(reference, body);

        // Seule une initiation explicitement acceptée par GU est un succès. Tout le reste (ex: HTTP 300
        // "Une operation similaire a ete envoyee il y a moins de 1 minutes", statut numérique d'erreur,
        // ou Wave sans payment_url) est un refus : la tentative est marquée échouée et le message GU renvoyé.
        String status = asString(firstNonNull(response, "status", "statut"));
        String paymentUrlCheck = asString(firstNonNull(response, "payment_url", "paymentUrl"));
        boolean accepted = status != null && INITIATED_STATUSES.contains(status.trim().toUpperCase())
                && (operator != MobileMoneyOperator.WAVE || paymentUrlCheck != null);
        if (!accepted) {
            String detail = asString(firstNonNull(response, "detailMessage", "message", "description"));
            log.warn("GU n'a pas initié {} : statut={}, détail={}", reference, status, detail);
            failQuietly(reference);
            throw new PaymentGatewayException(detail != null
                    ? label(operator) + " : " + detail + (detail.toLowerCase().contains("similaire")
                            ? " — patientez une minute puis relancez." : "")
                    : "Paiement non initié par " + label(operator) + " (statut " + status + ")");
        }

        String paymentUrl = asString(firstNonNull(response, "payment_url", "paymentUrl"));
        String idFromGU = asString(firstNonNull(response, "idFromGU"));
        log.info("Paiement {} initié via GU ({}) — idFromGU={}, statut={}", reference, operator, idFromGU, status);

        // Suivi pour la réconciliation : tant qu'elle n'est pas finalisée, son statut sera relu chez GU
        writeTx.executeWithoutResult(tx -> {
            MobileMoneyTransaction tracked = transactionRepository.findByReference(reference)
                    .orElseGet(() -> MobileMoneyTransaction.builder().reference(reference).build());
            tracked.setOperator(operator.name());
            tracked.setIdFromGU(idFromGU);
            tracked.setAmount(BigDecimal.valueOf(amount));
            tracked.setStatus("INITIATED");
            tracked.setFinalizedAt(null);
            tracked.setReturnUrl(clientReturnUrl);
            tracked.setCancelUrl(clientCancelUrl);
            transactionRepository.save(tracked);
        });

        return MobileMoneyPaymentResponseDTO.builder()
                .success(true)
                .message(paymentUrl != null
                        ? "Paiement initié. Ouvrez payment_url pour payer avec " + label(operator) + "."
                        : "Paiement initié. Validez la demande reçue sur votre téléphone " + label(operator) + ".")
                .reference(reference)
                .operator(operator)
                .serviceCode(operator.getServiceCode())
                .amount(BigDecimal.valueOf(amount))
                .fees(asDecimal(firstNonNull(response, "fees", "honoraires")))
                .idFromGU(asString(firstNonNull(response, "idFromGU")))
                .numTransaction(asString(firstNonNull(response, "numTransaction")))
                .status(status)
                .paymentUrl(paymentUrl)
                .returnUrl(finalRedirect(reference, true, clientReturnUrl, clientCancelUrl))
                .cancelUrl(finalRedirect(reference, false, clientReturnUrl, clientCancelUrl))
                .build();
    }

    // Initiation (PUT /{agence}/transaction) : toute erreur HTTP libère la tentative et renvoie un 502 clair
    private Map<String, Object> callGateway(String reference, Map<String, Object> body) {
        try {
            Map<String, Object> response = exchange(HttpMethod.valueOf(httpMethod.trim().toUpperCase()),
                    gatewayUri(), body);
            if (response == null) {
                failQuietly(reference);
                throw new PaymentGatewayException("Réponse vide du service de paiement");
            }
            return response;
        } catch (RestClientResponseException e) {
            log.error("GU a refusé la transaction {} : HTTP {} — {}", reference, e.getStatusCode().value(),
                    truncate(e.getResponseBodyAsString()));
            failQuietly(reference);
            throw new PaymentGatewayException(e.getStatusCode().value() == 401
                    ? "Authentification refusée par le service de paiement (HTTP 401) : vérifier GUTOUCH_DIGEST_USERNAME / GUTOUCH_DIGEST_PASSWORD"
                    : "Le service de paiement a refusé la demande (HTTP " + e.getStatusCode().value() + ")");
        } catch (ResourceAccessException e) {
            log.error("GU injoignable pour la transaction {} : {}", reference, e.getMessage());
            failQuietly(reference);
            throw new PaymentGatewayException("Le service de paiement est injoignable, réessayez dans quelques instants");
        }
    }

    // Statut réel d'une transaction chez GU : GET /{agence}/transaction/{idFromClient}.
    // Renvoie le statut GU (SUCCEED, FAILED, INITIATED...) ou null si inconnue / injoignable.
    private String fetchGatewayStatus(String reference) {
        URI uri = UriComponentsBuilder.fromHttpUrl(apiBaseUrl)
                .pathSegment(agencyCode, "transaction", reference)
                .queryParam("loginAgent", loginAgent)
                .queryParam("passwordAgent", passwordAgent)
                .encode()
                .build()
                .toUri();
        try {
            Map<String, Object> response = exchange(HttpMethod.GET, uri, null);
            return asString(firstNonNull(response, "status", "statut"));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                log.warn("Statut GU illisible pour {} : HTTP {}", reference, e.getStatusCode().value());
            }
            return null;
        } catch (ResourceAccessException e) {
            log.warn("GU injoignable pour le statut de {} : {}", reference, e.getMessage());
            return null;
        }
    }

    private URI gatewayUri() {
        return UriComponentsBuilder.fromHttpUrl(apiBaseUrl)
                .pathSegment(agencyCode, "transaction")
                .queryParam("loginAgent", loginAgent)
                .queryParam("passwordAgent", passwordAgent)
                .encode()
                .build()
                .toUri();
    }

    // Appel HTTP à l'API GU. L'URL contient le mot de passe agent : elle n'est JAMAIS journalisée.
    // GU protège l'API par une authentification HTTP Digest (MD5, qop=auth) : le premier appel reçoit
    // un 401 avec le défi WWW-Authenticate, on rejoue alors la requête avec l'en-tête Authorization.
    private Map<String, Object> exchange(HttpMethod method, URI uri, Map<String, Object> body) {
        try {
            return send(method, uri, body, null);
        } catch (RestClientResponseException e) {
            String challenge = e.getStatusCode().value() == 401 && e.getResponseHeaders() != null
                    ? e.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE) : null;
            if (challenge == null || !challenge.regionMatches(true, 0, "Digest", 0, 6)) {
                throw e;
            }
            return send(method, uri, body, DigestAuth.authorization(challenge, digestUsername, digestPassword,
                    method.name(), uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "")));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(HttpMethod method, URI uri, Map<String, Object> body, String authorization) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON);
            spec.body(body);
        }
        // HTTP 3xx (ex: 300 anti-doublon de GU) n'est pas une erreur pour RestClient : son body est
        // lu normalement puis rejeté par le contrôle de statut de createPayment
        return spec.retrieve().body(Map.class);
    }

    // L'initiation a échoué côté GU : la tentative est marquée échouée tout de suite (au lieu
    // d'attendre l'expiration des 5 min), pour que l'utilisateur puisse relancer immédiatement
    private void failQuietly(String reference) {
        try {
            paymentConfirmationService.routeCallbackByPrefix(reference, false);
        } catch (Exception e) {
            log.warn("Impossible de marquer {} comme échoué : {}", reference, e.getMessage());
        }
    }

    // =========================================================================
    // CONFIRMATION — une seule règle pour retour, confirmation front, callback et réconciliation :
    // le statut est TOUJOURS relu chez GU (jamais cru sur parole), puis appliqué une seule fois
    // =========================================================================

    // Relit le statut chez GU et, s'il est final (SUCCEED / FAILED), applique sous verrou la
    // confirmation métier existante (abonnement ACTIVE + paiement COMPLETED, charge payée et wallet
    // syndic crédité, travaux : wallet prestataire crédité...). Idempotent.
    // Renvoie SUCCEED, FAILED, INITIATED (en cours) ou null (référence inconnue chez GU / injoignable).
    private String reconcile(String reference) {
        String guStatus = fetchGatewayStatus(reference);
        if (guStatus == null) {
            return null;
        }
        String normalized = guStatus.trim().toUpperCase();
        boolean success = SUCCESS_STATUSES.contains(normalized);
        boolean failed = !success && FAILURE_STATUSES.contains(normalized);

        writeTx.executeWithoutResult(tx -> {
            MobileMoneyTransaction tracked = transactionRepository.findByReferenceForUpdate(reference)
                    .orElseGet(() -> transactionRepository.save(MobileMoneyTransaction.builder()
                            .reference(reference).operator("INCONNU").status("INITIATED").build()));
            tracked.setLastCheckedAt(LocalDateTime.now());
            if (tracked.getFinalizedAt() == null && (success || failed)) {
                Map<String, Object> result = paymentConfirmationService.routeCallbackByPrefix(reference, success).getBody();
                tracked.setStatus(success ? "SUCCEED" : "FAILED");
                tracked.setFinalizedAt(LocalDateTime.now());
                log.info("Paiement GU {} finalisé ({}) : {}", reference, tracked.getStatus(), result);
            }
            transactionRepository.save(tracked);
        });
        return success ? "SUCCEED" : failed ? "FAILED" : "INITIATED";
    }

    // Toutes les minutes : les transactions Wave / OM encore en cours (moins de 24 h) sont relues chez
    // GU. Un paiement réussi passe donc au statut payé même si le front n'appelle pas /confirm et si
    // le callback GU n'arrive pas (ex: backend local injoignable depuis GU).
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void reconcilePendingTransactions() {
        if (!isGatewayConfigured()) {
            return;
        }
        for (MobileMoneyTransaction t : transactionRepository
                .findByFinalizedAtIsNullAndCreatedAtAfter(LocalDateTime.now().minusHours(24))) {
            try {
                reconcile(t.getReference());
            } catch (Exception e) {
                log.warn("Réconciliation GU impossible pour {} : {}", t.getReference(), e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> confirmFromApp(String reference, String signature) {
        assertValidSignature(reference, signature);
        String status = reconcile(reference);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reference", reference);
        result.put("status", status != null ? status : "INCONNU");
        result.put("paid", "SUCCEED".equals(status));
        if ("SUCCEED".equals(status)) {
            result.put("success", true);
            result.put("message", "Paiement confirmé");
        } else if ("FAILED".equals(status)) {
            result.put("success", false);
            result.put("message", "Paiement échoué ou annulé chez l'opérateur");
        } else {
            result.put("success", true);
            result.put("message", "Paiement pas encore confirmé par l'opérateur — il sera pris en compte automatiquement");
        }
        return result;
    }

    @Override
    public String handleReturn(String reference, String signature) {
        assertValidSignature(reference, signature);
        String status = reconcile(reference);
        return redirectUrl(reference, !"FAILED".equals(status));
    }

    @Override
    public String handleCancel(String reference, String signature) {
        assertValidSignature(reference, signature);
        // Pas d'échec forcé : GU reste la source de vérité (FAILED ici, ou expiration en 5 min)
        reconcile(reference);
        return redirectUrl(reference, false);
    }

    // Le contenu du callback n'est jamais cru sur parole (webhook public) : il ne sert qu'à savoir
    // QUELLE référence relire chez GU. GU préfixe idFromClient par le code agence ("INIMA27104_SYR-…").
    @Override
    public Map<String, Object> handleCallback(Map<String, Object> payload) {
        String raw = asString(firstNonNull(payload, "idFromClient", "partner_transaction_id", "id_from_client"));
        log.info("Callback GU reçu : idFromClient={}, idFromGU={}, statut annoncé={}",
                raw, firstNonNull(payload, "idFromGU"), firstNonNull(payload, "status", "statut"));
        if (raw == null || raw.isBlank()) {
            return Map.of("success", false, "message", "Référence (idFromClient) manquante");
        }
        String reference = stripAgencyPrefix(raw.trim());
        String status = reconcile(reference);
        if (status == null) {
            return Map.of("success", false, "message", "Transaction inconnue chez GU : " + reference);
        }
        return Map.of("success", true, "reference", reference, "status", status);
    }

    private String stripAgencyPrefix(String idFromClient) {
        String prefix = agencyCode + "_";
        return idFromClient.startsWith(prefix) ? idFromClient.substring(prefix.length()) : idFromClient;
    }

    // =========================================================================
    // RÉSOLUTION DE LA RÉFÉRENCE → montant, payeur, coordonnées
    // =========================================================================

    private Payable resolvePayable(String reference) {
        if (reference == null || reference.length() < 4) {
            throw new BadRequestException("Référence de paiement invalide");
        }
        String prefix = reference.substring(0, 4);
        return switch (prefix) {
            case "SYN-", "SYR-", "SYA-" -> syndicSubscriptionRepository.findByTransactionRef(reference)
                    .map(s -> {
                        assertPending(reference, s.getPaymentStatus());
                        return payable(reference, s.getAmountPaid(), s.getInitiatedBy() != null ? s.getInitiatedBy() : s.getSyndic());
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable pour " + reference));
            case "SUB-", "PRA-" -> providerSubscriptionRepository.findByTransactionRef(reference)
                    .map(s -> {
                        assertPending(reference, s.getPaymentStatus());
                        return payable(reference, s.getAmountPaid(), s.getInitiatedBy() != null ? s.getInitiatedBy() : s.getProvider());
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable pour " + reference));
            case "CPY-" -> chargeCallPaymentRepository.findByReference(reference)
                    .map(p -> {
                        assertPending(reference, p.getStatus());
                        return payable(reference, p.getAmount(), p.getOwner());
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Paiement de charge introuvable pour " + reference));
            case "ECP-" -> exceptionalCallPaymentRepository.findByReference(reference)
                    .map(p -> {
                        assertPending(reference, p.getStatus());
                        return payable(reference, p.getAmount(), p.getOwner());
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Paiement de charge introuvable pour " + reference));
            case "PAY-", "SOL-" -> paymentRepository.findByReference(reference)
                    .map(p -> {
                        assertPending(reference, p.getStatus());
                        return payable(reference, p.getAmount(), p.getPaymentInitiator());
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Paiement de travaux introuvable pour " + reference));
            default -> throw new BadRequestException("Type de référence non supporté : " + reference);
        };
    }

    private Payable payable(String reference, BigDecimal amount, User payer) {
        if (payer == null) {
            throw new BadRequestException("Aucun payeur associé à " + reference);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Montant invalide pour " + reference);
        }
        return new Payable(reference, amount, payer.getId(), payer.getEmail(),
                payer.getFirstName(), payer.getLastName(), payer.getPhone());
    }

    // Seule une tentative encore en attente peut être payée. Une tentative expirée (FAILED après
    // 5 min sans confirmation) ou déjà payée doit être relancée depuis l'écran d'origine.
    private void assertPending(String reference, PaymentStatus status) {
        if (status != PaymentStatus.PENDING) {
            throw new BadRequestException("La transaction " + reference + " n'est plus en attente (statut "
                    + status + ") : relancez une nouvelle demande de paiement");
        }
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    private Audience audienceOf(String reference) {
        if (reference.startsWith("SYR-")) return Audience.SYNDIC_WEB;
        if (reference.startsWith("SYN-") || reference.startsWith("SYA-") || reference.startsWith("PRA-")) return Audience.ADMIN_WEB;
        return Audience.MOBILE;
    }

    // Redirection finale après /return ou /cancel : l'URL fournie par l'application mobile si elle
    // existe (mémorisée à l'initiation), sinon la page par défaut du parcours
    private String redirectUrl(String reference, boolean success) {
        MobileMoneyTransaction tracked = transactionRepository.findByReference(reference).orElse(null);
        return finalRedirect(reference, success,
                tracked != null ? tracked.getReturnUrl() : null,
                tracked != null ? tracked.getCancelUrl() : null);
    }

    // Page vers laquelle l'utilisateur arrive à la fin, avec ?paymentStatus=success|failed&ref=…
    //  - URLs du mobile : return_url si succès, cancel_url sinon (chacune retombe sur l'autre si absente)
    //  - sinon : application web syndic / admin, ou pages /payment-success | /payment-failed (WebView)
    private String finalRedirect(String reference, boolean success, String clientReturnUrl, String clientCancelUrl) {
        String query = "paymentStatus=" + (success ? "success" : "failed")
                + "&ref=" + URLEncoder.encode(reference, StandardCharsets.UTF_8);
        String client = success
                ? (clientReturnUrl != null ? clientReturnUrl : clientCancelUrl)
                : (clientCancelUrl != null ? clientCancelUrl : clientReturnUrl);
        if (client != null) {
            return appendQuery(client, query);
        }
        return switch (audienceOf(reference)) {
            case SYNDIC_WEB -> appendQuery(syndicReturnUrl, query);
            case ADMIN_WEB -> appendQuery(adminPage(reference), query);
            case MOBILE -> backendBaseUrl + "/api/payments/intouch/" + (success ? "payment-success" : "payment-failed")
                    + "?num_command=" + URLEncoder.encode(reference, StandardCharsets.UTF_8);
        };
    }

    private static String appendQuery(String url, String query) {
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    // URL de retour transmise à GU (return_url / cancel_url), toujours publique et en https (GU refuse
    // localhost) :
    //  - URLs fournies par le mobile, ou parcours mobile : le backend public (/return ou /cancel), qui
    //    vérifie le statut chez GU, applique le paiement, puis redirige vers l'URL finale (finalRedirect)
    //  - parcours web (syndic SYR-, admin SYN-/SYA-/PRA-) : directement la page de l'application, avec
    //    ref + sig ; l'application appelle ensuite GET /api/payments/intouch/gutouch/confirm?ref&sig
    private String returnUrl(String reference, boolean success, boolean clientRedirect) {
        if (clientRedirect || audienceOf(reference) == Audience.MOBILE) {
            return buildSignedUrl(success ? "/api/payments/intouch/gutouch/return"
                    : "/api/payments/intouch/gutouch/cancel", reference);
        }
        String query = "paymentStatus=" + (success ? "success" : "cancelled")
                + "&ref=" + URLEncoder.encode(reference, StandardCharsets.UTF_8) + "&sig=" + sign(reference);
        return audienceOf(reference) == Audience.SYNDIC_WEB
                ? appendQuery(syndicReturnUrl, query)
                : appendQuery(adminPage(reference), query);
    }

    // Page du back-office admin selon le parcours : liste des syndics après création d'un syndic
    // (SYN-), sinon la page admin par défaut (renouvellements SYA- / PRA-)
    private String adminPage(String reference) {
        return reference.startsWith("SYN-") ? adminSyndicCreatedReturnUrl : adminReturnUrl;
    }

    // URL de retour fournie par le client : absolue, avec un schéma (https, http ou deep link de l'app),
    // jamais javascript:/data:/file: — sinon 400. Renvoie null si absente.
    private static String validateClientUrl(String field, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.trim();
        if (value.length() > 500) {
            throw new BadRequestException(field + " : 500 caractères maximum");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(field + " invalide : " + value);
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : null;
        if (scheme == null || Set.of("javascript", "data", "file", "vbscript").contains(scheme)
                || ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() == null)) {
            throw new BadRequestException(field + " invalide (URL absolue ou deep link attendu) : " + value);
        }
        return value;
    }

    // Lien de retour signé (HMAC-SHA256 de la référence) : sans la signature, impossible de forger
    // un retour pour une autre référence
    private String buildSignedUrl(String path, String reference) {
        return backendBaseUrl + path + "?ref=" + URLEncoder.encode(reference, StandardCharsets.UTF_8)
                + "&sig=" + sign(reference);
    }

    private String sign(String reference) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(("gutouch:" + reference).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Signature du lien de retour impossible", e);
        }
    }

    private void assertValidSignature(String reference, String signature) {
        if (reference == null || signature == null
                || !MessageDigest.isEqual(sign(reference).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BadRequestException("Lien de retour de paiement invalide");
        }
    }

    private boolean isGatewayConfigured() {
        return !(agencyCode.isBlank() || loginAgent.isBlank() || passwordAgent.isBlank()
                || digestUsername.isBlank() || digestPassword.isBlank());
    }

    private void assertGatewayConfigured() {
        if (!isGatewayConfigured()) {
            throw new PaymentGatewayException("Paiement Wave / Orange Money non configuré (GUTOUCH_AGENCY_CODE, "
                    + "GUTOUCH_LOGIN_AGENT, GUTOUCH_PASSWORD_AGENT, GUTOUCH_DIGEST_USERNAME, GUTOUCH_DIGEST_PASSWORD)");
        }
    }

    // Numéro sénégalais à 9 chiffres attendu par GU (ex: 771234567) — on retire indicatif et séparateurs
    private static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Numéro Wave / Orange Money obligatoire (phoneNumber)");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00221")) digits = digits.substring(5);
        else if (digits.startsWith("221") && digits.length() == 12) digits = digits.substring(3);
        if (digits.length() != 9) {
            throw new BadRequestException("Numéro Wave / Orange Money invalide : 9 chiffres attendus (ex: 771234567)");
        }
        return digits;
    }

    // Le franc CFA n'a pas de décimales
    private static long toXof(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String label(MobileMoneyOperator operator) {
        return operator == MobileMoneyOperator.WAVE ? "Wave" : "Orange Money";
    }

    private static Object firstNonNull(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static BigDecimal asDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value).replace(',', '.').trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 500 ? s.substring(0, 500) + "…" : s);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
