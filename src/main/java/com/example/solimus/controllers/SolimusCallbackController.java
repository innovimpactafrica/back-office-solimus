package com.example.solimus.controllers;

import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.provider.ProviderService;
import com.example.solimus.services.provider.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/intouch")
@RequiredArgsConstructor
@Slf4j
public class SolimusCallbackController {

    // Repository des paiements d'intervention : acompte PAY-* et solde SOL-*
    private final PaymentRepository paymentRepository;

    // Repository de la demande d'intervention liée au paiement
    private final InterventionRequestRepository interventionRepository;

    // Repository des paiements de charges courantes : CPY-*
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;

    // Repository des lignes d'appels de charges courantes
    private final ChargeCallItemRepository chargeCallItemRepository;

    // Repository des paiements de charges exceptionnelles : ECP-*
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;

    // Repository des lignes d'appels exceptionnels
    private final ExceptionalCallItemRepository exceptionalCallItemRepository;

    // Repository du wallet syndic, pour créditer les charges payées
    private final SyndicWalletRepository syndicWalletRepository;

    // Repository des transactions du wallet syndic
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;

    // Service utilisé pour créditer le wallet du prestataire après confirmation InTouch
    private final ProviderService providerService;

    // Service wallet pour gérer les crédits du prestataire
    private final WalletService walletService;

    // Repository des abonnements prestataires : SUB-*
    private final SubscriptionRepository subscriptionRepository;

    // Service email pour notifier le prestataire après activation Premium
    private final EmailService emailService;

    // Repository des logs d'activité pour tracer les paiements
    private final ActivityLogRepository activityLogRepository;

    // Formateur de date pour l'email de confirmation Premium (ex: "01 Janvier 2026")
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);

    // =========================================================================
    // ENDPOINT PRINCIPAL — Appelé automatiquement par InTouch après paiement
    // =========================================================================
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody InTouchCallbackRequest request) {

        log.info("InTouch callback reçu: partnerTx={}, guTx={}, status={}",
                request.getPartnerTransactionId(),
                request.getGuTransactionId(),
                request.getStatus());

        // partner_transaction_id = notre référence interne
        // PAY-xxxxxx = acompte owner, SOL-xxxxxx = solde owner, SUB-xxxxxx = abonnement, CPY-xxxxxx = charge courante, ECP-xxxxxx = charge exceptionnelle
        String ref = request.getPartnerTransactionId();
        if (ref == null || ref.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Référence manquante"
            ));
        }

        // InTouch renvoie SUCCESSFUL quand le paiement est réellement confirmé
        String status = request.getStatus() != null
                ? request.getStatus().trim().toUpperCase() : "";
        boolean succes = "SUCCESSFUL".equals(status);

        // Routing selon le préfixe de la référence
        if (ref.startsWith("PAY-") || ref.startsWith("SOL-")) {
            // Paiement intervention owner : acompte ou solde
            return handleOwnerInterventionPaymentCallback(ref, succes);
        }

        if (ref.startsWith("SUB-")) {
            // On route vers le traitement spécifique à l'abonnement
            return handleSubscriptionCallback(ref, succes);
        }

        if (ref.startsWith("CPY-")) {
            // Paiement charge courante copropriétaire
            return handleChargePaymentCallback(ref, succes);
        }

        if (ref.startsWith("ECP-")) {
            // Paiement charge exceptionnelle copropriétaire
            return handleExceptionalChargePaymentCallback(ref, succes);
        }

        // Référence non reconnue
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Type de référence non supporté : " + ref
        ));
    }

    // =========================================================================
    // CAS 1 — Paiement intervention owner (PAY- = acompte, SOL- = solde)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleOwnerInterventionPaymentCallback(
            String ref, boolean succes) {

        return paymentRepository.findByReference(ref)
                .map(payment -> {

                    // Sécurité anti-double callback
                    if (payment.getStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement déjà confirmé"
                        ));
                    }

                    // Paiement échoué → on ne crédite pas le wallet
                    if (!succes) {
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(payment);

                        log.warn("Paiement intervention owner échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement marqué comme échoué"
                        ));
                    }

                    // Paiement confirmé par InTouch → on met à jour le statut
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setPaidAt(LocalDateTime.now());
                    paymentRepository.save(payment);

                    // Le paiement provient du Mobile Money (argent externe) → on crédite le wallet du prestataire
                    walletService.creditWallet(payment.getProvider().getId(), payment.getAmount());

                    // Synchronisation financière de la demande d'intervention
                    InterventionRequest req = payment.getInterventionRequest();
                    if (payment.getType() == PaymentType.ACOMPTE) {
                        // Après acompte : depositAmount = montant acompte versé
                        // remainingAmount = totalAmount - acompte
                        req.setDepositAmount(payment.getAmount());
                        req.setRemainingAmount(
                                req.getTotalAmount().subtract(payment.getAmount()));

                        log.info("Acompte owner {} confirmé — reste à payer : {} FCFA",
                                ref, req.getRemainingAmount());

                    } else if (payment.getType() == PaymentType.SOLDE) {
                        // Après solde : tout est payé
                        req.setDepositAmount(
                                req.getTotalAmount() != null
                                        ? req.getTotalAmount()
                                        : BigDecimal.ZERO);
                        req.setRemainingAmount(BigDecimal.ZERO);

                        // Le paiement du solde valide définitivement l'intervention
                        req.addStatusHistory(InterventionStatus.FINAL_VALIDATION, payment.getPaymentInitiator());
                        req.setValidatedAt(LocalDateTime.now());

                        log.info("Solde owner {} confirmé — intervention {} clôturée",
                                ref, req.getId());
                    }

                    // Sauvegarde finale
                    interventionRepository.save(req);

                    // Tracer l'activité de paiement
                    ActivityLog activityLog = new ActivityLog();
                    activityLog.setResidence(req.getResidence());
                    activityLog.setType(ActivityType.PAYMENT_RECEIVED);
                    activityLog.setRelatedEntityType("INTERVENTION_PAYMENT");
                    activityLog.setRelatedEntityId(payment.getId());
                    activityLog.setActor(payment.getPaymentInitiator());
                    String paymentTypeLabel = payment.getType() == PaymentType.ACOMPTE ? "Acompte" : "Solde";
                    activityLog.setMessage("Paiement intervention owner reçu");
                    activityLog.setDetail(paymentTypeLabel + " — " + req.getTitle() + " — " + payment.getAmount() + " FCFA");
                    activityLogRepository.save(activityLog);

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Callback traité avec succès"
                    ));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        Map.<String, Object>of(
                                "success", false,
                                "message", "Paiement introuvable pour la référence : " + ref
                        )
                ));
    }


    // =========================================================================
    // CAS 2 — Paiement abonnement Premium (SUB-)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleSubscriptionCallback(String ref, boolean succes) {

        // On retrouve la Subscription créée en PENDING grâce à sa référence unique
        return subscriptionRepository.findByTransactionRef(ref)
                .map(subscription -> {

                    // Anti-double callback : si TouchPay rappelle deux fois, on ne réagit pas la 2e fois
                    if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Abonnement déjà activé"
                        ));
                    }

                    if (!succes) {
                        // Le paiement a échoué côté TouchPay → on met à échec cette tentative
                        subscription.setStatus(SubscriptionStatus.FAILED);
                        subscriptionRepository.save(subscription);

                        // On trace l'échec dans les logs pour debug
                        log.warn("Paiement abonnement échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement abonnement marqué comme échoué"
                        ));
                    }

                    // Le paiement est confirmé par TouchPay → on débloque réellement l'accès
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscriptionRepository.save(subscription);

                    // On trace l'activation réussie, avec la date d'expiration pour suivi
                    log.info("Abonnement {} activé pour prestataire {} — expire le {}",
                            ref,
                            subscription.getProvider().getId(),
                            subscription.getEndDate().format(DATE_FORMATTER));

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Abonnement activé avec succès"
                    ));
                })
                // Si la référence n'existe pas du tout en base (cas anormal)
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        Map.<String, Object>of(
                                "success", false,
                                "message", "Abonnement introuvable pour la référence : " + ref
                        )
                ));
    }


    // =========================================================================
    // CAS 3 — Paiement charge courante copropriétaire (CPY-)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleChargePaymentCallback(
            String ref, boolean succes) {

        return chargeCallPaymentRepository.findByReference(ref)
                .map(paiement -> {

                    // Anti-double callback
                    if (paiement.getStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement déjà confirmé"
                        ));
                    }

                    if (!succes) {
                        paiement.setStatus(PaymentStatus.FAILED);
                        chargeCallPaymentRepository.save(paiement);
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement marqué comme échoué"
                        ));
                    }

                    //sinon
                    // Confirmer le paiement
                    paiement.setStatus(PaymentStatus.COMPLETED);
                    paiement.setPaidAt(LocalDateTime.now());
                    chargeCallPaymentRepository.save(paiement);

                    // Met à jour la ligne de charge : ajoute ce paiement au montant déjà payé
                    ChargeCallItem item = paiement.getChargeCallItem();
                    item.setPaidAmount(item.getPaidAmount().add(paiement.getAmount()));
                    chargeCallItemRepository.save(item);

                    // Crédite le wallet du syndic (catégorie CHARGES)
                    Residence residence = item.getChargeCall().getBudget().getResidence();
                    SyndicWallet syndicWallet = syndicWalletRepository
                            .findBySyndicId(residence.getSyndic().getId())
                            .orElseThrow(() -> new RuntimeException("Wallet syndic introuvable"));

                    SyndicWalletTransaction transaction = new SyndicWalletTransaction();
                    transaction.setWallet(syndicWallet);
                    transaction.setResidence(residence);
                    transaction.setCoOwner(paiement.getOwner());
                    transaction.setCategory(WalletTransactionCategory.CHARGES);
                    transaction.setAmount(paiement.getAmount());
                    transaction.setLabel("Paiement charges — " + item.getReference());
                    transaction.setBeneficiaryName(paiement.getOwner().getFirstName() + " " + paiement.getOwner().getLastName());
                    transaction.setMode(paiement.getMethod() != null ? paiement.getMethod().name() : null);
                    transaction.setTransactionDate(LocalDateTime.now());
                    transaction.setReference(paiement.getReference());
                    syndicWalletTransactionRepository.save(transaction);

                    // Tracer l'activité de paiement
                    ActivityLog activityLog = new ActivityLog();
                    activityLog.setResidence(residence);
                    activityLog.setType(ActivityType.PAYMENT_RECEIVED);
                    activityLog.setRelatedEntityType("CHARGE_CALL_PAYMENT");
                    activityLog.setRelatedEntityId(paiement.getId());
                    activityLog.setActor(paiement.getOwner());
                    activityLog.setMessage("Paiement charges reçu");
                    activityLog.setDetail(item.getReference() + " — " + paiement.getAmount() + " FCFA");
                    activityLogRepository.save(activityLog);

                    log.info("Charge {} payée avec succès — item {}", ref, item.getId());

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Paiement charge confirmé"
                    ));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        Map.<String, Object>of(
                                "success", false,
                                "message", "Paiement charge introuvable : " + ref
                        )
                ));
    }

    // =========================================================================
    // CAS 4 — Paiement charge exceptionnelle copropriétaire (ECP-)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleExceptionalChargePaymentCallback(
            String ref, boolean succes) {

        return exceptionalCallPaymentRepository.findByReference(ref)
                .map(paiement -> {

                    // Anti-double callback
                    if (paiement.getStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement déjà confirmé"
                        ));
                    }

                    if (!succes) {
                        paiement.setStatus(PaymentStatus.FAILED);
                        exceptionalCallPaymentRepository.save(paiement);
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement marqué comme échoué"
                        ));
                    }

                    //sinon, Confirmer le paiement
                    paiement.setStatus(PaymentStatus.COMPLETED);
                    paiement.setPaidAt(LocalDateTime.now());
                    exceptionalCallPaymentRepository.save(paiement);

                    // Met à jour la ligne d'appel exceptionnel
                    ExceptionalCallItem item = paiement.getExceptionalCallItem();
                    item.setPaidAmount(item.getPaidAmount().add(paiement.getAmount()));
                    exceptionalCallItemRepository.save(item);

                    // Crédite le wallet du syndic (catégorie CHARGES)
                    Residence residence = item.getExceptionalCall().getResidence();
                    SyndicWallet syndicWallet = syndicWalletRepository
                            .findBySyndicId(residence.getSyndic().getId())
                            .orElseThrow(() -> new RuntimeException("Wallet syndic introuvable"));

                    SyndicWalletTransaction transaction = new SyndicWalletTransaction();
                    transaction.setWallet(syndicWallet);
                    transaction.setResidence(residence);
                    transaction.setCoOwner(paiement.getOwner());
                    transaction.setCategory(WalletTransactionCategory.CHARGES);
                    transaction.setAmount(paiement.getAmount());
                    transaction.setLabel("Paiement charge exceptionnelle — " + item.getExceptionalCall().getTitle());
                    transaction.setBeneficiaryName(paiement.getOwner().getFirstName() + " " + paiement.getOwner().getLastName());
                    transaction.setMode(paiement.getMethod() != null ? paiement.getMethod().name() : null);
                    transaction.setTransactionDate(LocalDateTime.now());
                    transaction.setReference(paiement.getReference());
                    syndicWalletTransactionRepository.save(transaction);

                    // Tracer l'activité de paiement
                    ActivityLog activityLog = new ActivityLog();
                    activityLog.setResidence(residence);
                    activityLog.setType(ActivityType.PAYMENT_RECEIVED);
                    activityLog.setRelatedEntityType("EXCEPTIONAL_CALL_PAYMENT");
                    activityLog.setRelatedEntityId(paiement.getId());
                    activityLog.setActor(paiement.getOwner());
                    activityLog.setMessage("Paiement charge exceptionnelle reçu");
                    activityLog.setDetail(item.getExceptionalCall().getTitle() + " — " + paiement.getAmount() + " FCFA");
                    activityLogRepository.save(activityLog);

                    log.info("Charge exceptionnelle {} payée avec succès — item {}", ref, item.getId());

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Paiement charge exceptionnelle confirmé"
                    ));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        Map.<String, Object>of(
                                "success", false,
                                "message", "Paiement charge exceptionnelle introuvable : " + ref
                        )
                ));
    }
}