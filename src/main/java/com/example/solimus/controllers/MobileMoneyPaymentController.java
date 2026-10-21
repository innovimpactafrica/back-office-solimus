package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.subscription.AdminRenewSubscriptionDTO;
import com.example.solimus.dtos.admin.syndic.CreateSyndicDTO;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.charge.InitierPaiementChargeDTO;
import com.example.solimus.dtos.owner.travaux.ValiderTravauxDTO;
import com.example.solimus.dtos.payment.MobileMoneyAdminRenewDTO;
import com.example.solimus.dtos.payment.MobileMoneyCreateSyndicDTO;
import com.example.solimus.dtos.payment.MobileMoneyDepositDTO;
import com.example.solimus.dtos.payment.MobileMoneyPaymentRequestDTO;
import com.example.solimus.dtos.payment.MobileMoneyPlanChangeDTO;
import com.example.solimus.dtos.payment.MobileMoneyProviderSubscriptionDTO;
import com.example.solimus.dtos.payment.MobileMoneyRedirectDTO;
import com.example.solimus.dtos.payment.MobileMoneySyndicRenewDTO;
import com.example.solimus.dtos.payment.MobileMoneyPaymentResponseDTO;
import com.example.solimus.dtos.provider.subscription.InitiateSubscriptionPaymentDTO;
import com.example.solimus.dtos.syndic.subscription.InitiateSyndicPlanChangeDTO;
import com.example.solimus.dtos.syndic.subscription.RenewSyndicSubscriptionDTO;
import com.example.solimus.dtos.syndic.travaux.PayDepositDTO;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.MobileMoneyOperator;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.services.admin.subscription.PlanService;
import com.example.solimus.services.admin.syndic.SyndicService;
import com.example.solimus.services.owner.charge.OwnerChargeService;
import com.example.solimus.services.owner.travaux.ownerTravauxService;
import com.example.solimus.services.payment.MobileMoneyPaymentService;
import com.example.solimus.services.provider.subscription.SubscriptionPaymentService;
import com.example.solimus.services.syndic.subscription.SyndicSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Paiements Wave / Orange Money via l'API TouchPay (GU) — alternative au pont TouchPay "hosted"
 * (touchpay-bridge.html), qui reste disponible. Chaque endpoint renvoie payment_url à ouvrir.
 *
 * Deux façons de payer :
 *  - endpoints "/mobile-money" par parcours : créent la tentative (même logique métier que le flux
 *    TouchPay existant) ET lancent le paiement Wave / OM en un seul appel ;
 *  - POST /api/payments/mobile-money/{reference} : paie une référence déjà initiée (SYR-, CPY-, ...).
 *
 * Le passage au statut payé (COMPLETED / abonnement ACTIVE / wallet crédité) se fait au retour
 * signé (return_url) ou au callback GU, via la même logique que TouchPay (PaymentConfirmationService).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Paiements - Wave / Orange Money", description = "Paiements via l'API TouchPay (GU) : abonnements, renouvellements, charges et travaux")
public class MobileMoneyPaymentController {

    private final MobileMoneyPaymentService mobileMoneyPaymentService;
    private final SyndicService syndicService;
    private final PlanService planService;
    private final SyndicSubscriptionService syndicSubscriptionService;
    private final SubscriptionPaymentService subscriptionPaymentService;
    private final OwnerChargeService ownerChargeService;
    private final ownerTravauxService ownerTravauxService;
    private final Validator validator;

    private static final String OPERATOR_DOC = "WAVE ou ORANGE_MONEY";
    private static final String PHONE_DOC = "Numéro Wave / Orange Money à débiter (9 chiffres) — défaut : téléphone du profil";

    // =========================================================================
    // ENDPOINT GÉNÉRIQUE — payer une référence déjà initiée
    // =========================================================================

    @Operation(summary = "Payer une transaction en attente avec Wave ou Orange Money",
            description = "Référence créée par un des flux existants (SYN-, SYR-, SYA-, PRA-, SUB-, CPY-, ECP-, PAY-, SOL-) "
                    + "et encore en attente. Le montant facturé est celui du serveur ; 'amount' n'est qu'un contrôle. "
                    + "Seul le payeur de la transaction peut la régler.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement initié — ouvrir payment_url"),
            @ApiResponse(responseCode = "400", description = "Référence non en attente, montant incohérent ou numéro invalide",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas le payeur de cette transaction",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Référence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "502", description = "Le service de paiement a refusé ou n'a pas répondu",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/api/payments/mobile-money/{reference}")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> payReference(
            @PathVariable String reference,
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Valid @RequestBody(required = false) MobileMoneyPaymentRequestDTO dto) {
        return ResponseEntity.ok(mobileMoneyPaymentService.initiatePayment(reference, operator, dto));
    }

    // =========================================================================
    // ABONNEMENTS SYNDIC
    // =========================================================================

    @Operation(summary = "[Admin] Créer un syndic et payer son abonnement avec Wave / Orange Money",
            description = "Même création que POST /api/admin/syndics (compte actif, identifiants par email, abonnement en attente), "
                    + "puis paiement Wave / OM par l'admin. Le mode de paiement est déduit de 'operator'.")
    @PostMapping("/api/admin/syndics/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> createSyndicAndPay(
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @Valid @RequestBody MobileMoneyCreateSyndicDTO body) {
        CreateSyndicDTO dto = new CreateSyndicDTO();
        dto.setFirstName(body.getFirstName());
        dto.setLastName(body.getLastName());
        dto.setPhone(body.getPhone());
        dto.setEmail(body.getEmail());
        dto.setCompanyName(body.getCompanyName());
        dto.setCity(body.getCity());
        dto.setCountry(body.getCountry());
        dto.setAddress(body.getAddress());
        dto.setLatitude(body.getLatitude());
        dto.setLongitude(body.getLongitude());
        dto.setSyndicPlanId(body.getSyndicPlanId());
        dto.setDuration(body.getDuration());
        dto.setStartDate(body.getStartDate());
        dto.setMethod(operator.getPaymentMethod());
        validate(dto);
        mobileMoneyPaymentService.preflight(phoneNumber, null, null);
        String reference = syndicService.createSyndic(dto).getTransactionReference();
        return ResponseEntity.status(HttpStatus.CREATED).body(pay(reference, operator, phoneNumber));
    }

    @Operation(summary = "[Admin] Renouveler l'abonnement d'un abonné (syndic ou prestataire) avec Wave / Orange Money",
            description = "Même logique que POST /api/admin/subscriptions/subscribers/{id}/renew (options notifyClient / "
                    + "sendInvoiceEmail respectées au paiement).")
    @PostMapping("/api/admin/subscriptions/subscribers/{subscriptionId}/renew/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> renewSubscriberAndPay(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType,
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @Valid @RequestBody MobileMoneyAdminRenewDTO body) {
        AdminRenewSubscriptionDTO dto = new AdminRenewSubscriptionDTO();
        dto.setPlanId(body.getPlanId());
        dto.setDuration(body.getDuration());
        dto.setStartDate(body.getStartDate());
        dto.setNotifyClient(body.isNotifyClient());
        dto.setSendInvoiceEmail(body.isSendInvoiceEmail());
        dto.setMethod(operator.getPaymentMethod());
        validate(dto);
        mobileMoneyPaymentService.preflight(phoneNumber, null, null);
        String reference = planService.renewSubscriber(subscriptionId, subscriberType, dto).getTransactionReference();
        return ResponseEntity.ok(pay(reference, operator, phoneNumber));
    }

    @Operation(summary = "[Syndic] Changer de formule et payer avec Wave / Orange Money",
            description = "Même logique que POST /api/syndic/settings/subscription/change-plan.")
    @PostMapping("/api/syndic/settings/subscription/change-plan/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_SYNDIC')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> changePlanAndPay(
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @Valid @RequestBody MobileMoneyPlanChangeDTO body) {
        InitiateSyndicPlanChangeDTO dto = new InitiateSyndicPlanChangeDTO();
        dto.setSyndicPlanId(body.getSyndicPlanId());
        dto.setDuration(body.getDuration());
        dto.setMethod(operator.getPaymentMethod());
        validate(dto);
        mobileMoneyPaymentService.preflight(phoneNumber, null, null);
        String reference = syndicSubscriptionService.initiateChangePlan(dto).getTransactionReference();
        return ResponseEntity.ok(pay(reference, operator, phoneNumber));
    }

    @Operation(summary = "[Syndic] Renouveler ma formule actuelle et payer avec Wave / Orange Money",
            description = "Même logique que POST /api/syndic/settings/subscription/renew. Body facultatif : {\"duration\": \"MONTHLY\"}.")
    @PostMapping("/api/syndic/settings/subscription/renew/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_SYNDIC')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> renewAndPay(
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @RequestBody(required = false) MobileMoneySyndicRenewDTO body) {
        RenewSyndicSubscriptionDTO dto = new RenewSyndicSubscriptionDTO();
        dto.setDuration(body != null ? body.getDuration() : null);
        dto.setMethod(operator.getPaymentMethod());
        validate(dto);
        mobileMoneyPaymentService.preflight(phoneNumber, null, null);
        String reference = syndicSubscriptionService.initiateRenewal(dto).getTransactionReference();
        return ResponseEntity.ok(pay(reference, operator, phoneNumber));
    }

    // =========================================================================
    // ABONNEMENT PRESTATAIRE
    // =========================================================================

    @Operation(summary = "[Prestataire] Souscrire / renouveler mon abonnement et payer avec Wave / Orange Money",
            description = "Même logique que POST /api/provider/subscription/initiate. return_url / cancel_url (facultatives) : "
                    + "où renvoyer l'app mobile après paiement, avec ?paymentStatus=success|failed&ref=….")
    @PostMapping("/api/provider/subscription/initiate/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_PRESTATAIRE')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> providerSubscribeAndPay(
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @Valid @RequestBody MobileMoneyProviderSubscriptionDTO body) {
        InitiateSubscriptionPaymentDTO dto = new InitiateSubscriptionPaymentDTO();
        dto.setProviderPlanId(body.getProviderPlanId());
        dto.setAnnual(body.getAnnual());
        dto.setMethod(operator.getPaymentMethod());
        validate(dto);
        mobileMoneyPaymentService.preflight(phoneNumber, body.getReturnUrl(), body.getCancelUrl());
        String reference = subscriptionPaymentService.initiatePayment(dto).getTransactionReference();
        return ResponseEntity.ok(pay(reference, operator, phoneNumber, body.getReturnUrl(), body.getCancelUrl()));
    }

    // =========================================================================
    // COPROPRIÉTAIRE : CHARGES ET TRAVAUX
    // =========================================================================

    @Operation(summary = "[Copropriétaire] Payer une charge (courante ou exceptionnelle) avec Wave / Orange Money",
            description = "Même logique que POST /api/coowner/charges/{type}/{id}/payment (type = REGULAR ou EXCEPTIONAL). "
                    + "Le montant est le reste dû calculé par le serveur. Body facultatif : {\"return_url\", \"cancel_url\"} de l'app mobile.")
    @PostMapping("/api/coowner/charges/{type}/{id}/payment/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> payChargeWithMobileMoney(
            @PathVariable ChargeType type,
            @PathVariable Long id,
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @Valid @RequestBody(required = false) MobileMoneyRedirectDTO body) {
        String returnUrl = body != null ? body.getReturnUrl() : null;
        String cancelUrl = body != null ? body.getCancelUrl() : null;
        InitierPaiementChargeDTO dto = new InitierPaiementChargeDTO();
        dto.setMethod(operator.getChargePaymentMethod());
        mobileMoneyPaymentService.preflight(phoneNumber, returnUrl, cancelUrl);
        String reference = ownerChargeService.initierPaiement(type, id, dto).getTransactionReference();
        return ResponseEntity.ok(pay(reference, operator, phoneNumber, returnUrl, cancelUrl));
    }

    @Operation(summary = "[Copropriétaire] Payer l'acompte de travaux avec Wave / Orange Money",
            description = "Même logique que POST /api/owner/travaux/interventions/{id}/deposit (montant ≤ TTC du devis). "
                    + "Body : {\"montant\": 10000, \"return_url\", \"cancel_url\"} (URLs facultatives de l'app mobile).")
    @PostMapping("/api/owner/travaux/interventions/{interventionId}/deposit/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> payOwnerDepositWithMobileMoney(
            @PathVariable Long interventionId,
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @Valid @RequestBody MobileMoneyDepositDTO body) {
        PayDepositDTO dto = new PayDepositDTO();
        dto.setMontant(body.getMontant());
        dto.setMethode(operator.getPaymentMethod());
        validate(dto);
        mobileMoneyPaymentService.preflight(phoneNumber, body.getReturnUrl(), body.getCancelUrl());
        String reference = ownerTravauxService.payDeposit(interventionId, dto).getTransactionReference();
        return ResponseEntity.ok(pay(reference, operator, phoneNumber, body.getReturnUrl(), body.getCancelUrl()));
    }

    @Operation(summary = "[Copropriétaire] Valider les travaux et payer le solde avec Wave / Orange Money",
            description = "Même logique que POST /api/owner/travaux/interventions/{id}/balance (solde = TTC − acompte). "
                    + "Body facultatif : {\"return_url\", \"cancel_url\"} de l'app mobile.")
    @PostMapping("/api/owner/travaux/interventions/{interventionId}/balance/mobile-money")
    @PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<MobileMoneyPaymentResponseDTO> payOwnerBalanceWithMobileMoney(
            @PathVariable Long interventionId,
            @Parameter(description = OPERATOR_DOC) @RequestParam MobileMoneyOperator operator,
            @Parameter(description = PHONE_DOC) @RequestParam(required = false) String phoneNumber,
            @Valid @RequestBody(required = false) MobileMoneyRedirectDTO body) {
        String returnUrl = body != null ? body.getReturnUrl() : null;
        String cancelUrl = body != null ? body.getCancelUrl() : null;
        ValiderTravauxDTO dto = new ValiderTravauxDTO();
        dto.setMethode(operator.getPaymentMethod());
        mobileMoneyPaymentService.preflight(phoneNumber, returnUrl, cancelUrl);
        String reference = ownerTravauxService.validateAndPayBalance(interventionId, dto).getTransactionReference();
        return ResponseEntity.ok(pay(reference, operator, phoneNumber, returnUrl, cancelUrl));
    }

    // =========================================================================
    // PUBLIC (sans token) : retour navigateur et callback GU
    // Sous /api/payments/intouch/** : déjà ouvert sans authentification par SecurityConfig
    // =========================================================================

    @Operation(summary = "Retour après paiement (return_url envoyé à GU) — confirme le paiement puis redirige vers l'application",
            description = "Lien signé (ref + sig) généré par le serveur : met la transaction au statut payé "
                    + "(même traitement que le callback TouchPay), puis redirige (302) vers l'application.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirection vers l'application (paymentStatus=success)"),
            @ApiResponse(responseCode = "400", description = "Lien invalide (signature)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/api/payments/intouch/gutouch/return")
    public ResponseEntity<Void> handleReturn(@RequestParam String ref, @RequestParam String sig) {
        return redirect(mobileMoneyPaymentService.handleReturn(ref, sig));
    }

    @Operation(summary = "Confirmer un paiement depuis l'application (return_url web)",
            description = "Après paiement, Wave / Orange Money renvoie le navigateur sur la page de l'application "
                    + "(ex: https://syndic.solimus.sn/app/parametres?paymentStatus=success&ref=…&sig=…). L'application "
                    + "transmet ref et sig ici : la transaction passe au statut payé (idempotent).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement confirmé (ou déjà confirmé)"),
            @ApiResponse(responseCode = "400", description = "Lien invalide (signature) ou paiement non confirmable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/api/payments/intouch/gutouch/confirm")
    public ResponseEntity<Map<String, Object>> confirmFromApp(@RequestParam String ref, @RequestParam String sig) {
        Map<String, Object> result = mobileMoneyPaymentService.confirmFromApp(ref, sig);
        return Boolean.TRUE.equals(result.get("success")) ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @Operation(summary = "Retour après annulation (cancel_url envoyé à GU) — marque le paiement échoué puis redirige")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirection vers l'application (paymentStatus=failed)"),
            @ApiResponse(responseCode = "400", description = "Lien invalide (signature)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/api/payments/intouch/gutouch/cancel")
    public ResponseEntity<Void> handleCancel(@RequestParam String ref, @RequestParam String sig) {
        return redirect(mobileMoneyPaymentService.handleCancel(ref, sig));
    }

    @Operation(summary = "Webhook serveur-à-serveur GU (champ 'callback') — confirme ou fait échouer le paiement",
            description = "Lit idFromClient (= notre référence) et status. Idempotent : un paiement déjà confirmé n'est jamais retraité.")
    @PostMapping("/api/payments/intouch/gutouch/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = mobileMoneyPaymentService.handleCallback(payload);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    private MobileMoneyPaymentResponseDTO pay(String reference, MobileMoneyOperator operator, String phoneNumber) {
        return pay(reference, operator, phoneNumber, null, null);
    }

    private MobileMoneyPaymentResponseDTO pay(String reference, MobileMoneyOperator operator, String phoneNumber,
                                              String returnUrl, String cancelUrl) {
        MobileMoneyPaymentRequestDTO request = MobileMoneyPaymentRequestDTO.builder().phoneNumber(phoneNumber).build();
        request.setReturnUrl(returnUrl);
        request.setCancelUrl(cancelUrl);
        return mobileMoneyPaymentService.initiatePayment(reference, operator, request);
    }

    // Validation Bean Validation APRÈS avoir posé le mode de paiement déduit de l'opérateur
    // (les DTO existants le rendent obligatoire) — erreurs au format habituel (400)
    private <T> void validate(T dto) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
