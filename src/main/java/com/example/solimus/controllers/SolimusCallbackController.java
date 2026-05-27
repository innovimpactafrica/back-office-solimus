package com.example.solimus.controllers;

import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.InTouchCallbackRequest;
import com.example.solimus.entities.Subscription;
import com.example.solimus.entities.SubscriptionPayment;
import com.example.solimus.enums.*;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.provider.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // Repository des paiements d'abonnement Premium : SUB-*
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;

    // Repository des abonnements prestataires
    private final SubscriptionRepository subscriptionRepository;

    // Service utilisé pour créditer le wallet du prestataire après confirmation InTouch
    private final ProviderService providerService;

    // Service email pour notifier le prestataire après activation Premium
    private final EmailService emailService;

    // Formateur de date pour l'email de confirmation Premium (ex: "01 Janvier 2026")
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);

    // =========================================================================
    // ENDPOINT PRINCIPAL — Appelé automatiquement par InTouch après paiement
    // =========================================================================
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestBody InTouchCallbackRequest request) {

        log.info("InTouch callback reçu: partnerTx={}, guTx={}, status={}",
                request.getPartnerTransactionId(),
                request.getGuTransactionId(),
                request.getStatus());

        // partner_transaction_id = notre référence interne
        // PAY-xxxxxx = acompte, SOL-xxxxxx = solde, SUB-xxxxxx = abonnement
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
            // Paiement intervention : acompte ou solde
            return handleInterventionPaymentCallback(ref, succes);
        }

        if (ref.startsWith("SUB-")) {
            // Paiement abonnement Premium prestataire
            return handleSubscriptionCallback(ref, succes);
        }

        // Référence non reconnue
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Type de référence non supporté : " + ref
        ));
    }

    // =========================================================================
    // CAS 1 — Paiement intervention (PAY- = acompte, SOL- = solde)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleInterventionPaymentCallback(
            String ref, boolean succes) {

        return paymentRepository.findByReference(ref)
                .map(payment -> {

                    // Sécurité anti-double callback
                    // InTouch peut parfois rappeler plusieurs fois le même callback
                    if (payment.getStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement déjà confirmé"
                        ));
                    }

                    // Paiement échoué → on ne crédite pas le wallet
                    // Les montants de la demande restent inchangés
                    if (!succes) {
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(payment);

                        log.warn("Paiement intervention échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement marqué comme échoué"
                        ));
                    }

                    // Paiement confirmé par InTouch → on met à jour le statut
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setPaidAt(LocalDateTime.now());
                    paymentRepository.save(payment);

                    // Le prestataire reçoit l'argent uniquement après confirmation réelle
                    providerService.crediterWallet(
                            payment.getProvider().getId(),
                            payment.getAmount()
                    );

                    // Synchronisation financière de la demande d'intervention
                    InterventionRequest req = payment.getInterventionRequest();

                    if (payment.getType() == PaymentType.ACOMPTE) {
                        // Après acompte : depositAmount = montant acompte versé
                        // remainingAmount = totalAmount - acompte
                        req.setDepositAmount(payment.getAmount());
                        req.setRemainingAmount(
                                req.getTotalAmount().subtract(payment.getAmount()));

                        log.info("Acompte {} confirmé — reste à payer : {} FCFA",
                                ref, req.getRemainingAmount());

                    } else if (payment.getType() == PaymentType.SOLDE) {
                        // Après solde : tout est payé
                        // depositAmount = totalAmount pour que remainingAmount = 0 après @PreUpdate
                        req.setDepositAmount(
                                req.getTotalAmount() != null
                                        ? req.getTotalAmount()
                                        : BigDecimal.ZERO);
                        req.setRemainingAmount(BigDecimal.ZERO);

                        // Le paiement du solde valide définitivement l'intervention
                        req.addStatusHistory(InterventionStatus.FINAL_VALIDATION, payment.getSyndic());
                        req.setValidatedAt(LocalDateTime.now());

                        log.info("Solde {} confirmé — intervention {} clôturée",
                                ref, req.getId());
                    }

                    // Sauvegarde finale — déclenche aussi @PreUpdate dans InterventionRequest
                    interventionRepository.save(req);

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
    private ResponseEntity<Map<String, Object>> handleSubscriptionCallback(
            String ref, boolean succes) {

        return subscriptionPaymentRepository.findByReference(ref)
                .map(paiement -> {

                    // Sécurité anti-double callback
                    if (paiement.getStatut() == SubscriptionPaymentStatus.PAYE) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Abonnement déjà activé"
                        ));
                    }

                    // Paiement échoué → le prestataire reste sur son plan actuel
                    if (!succes) {
                        paiement.setStatut(SubscriptionPaymentStatus.ECHOUE);
                        subscriptionPaymentRepository.save(paiement);

                        log.warn("Paiement abonnement échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement abonnement marqué comme échoué"
                        ));
                    }

                    // Paiement confirmé → marquer comme payé
                    paiement.setStatut(SubscriptionPaymentStatus.PAYE);
                    subscriptionPaymentRepository.save(paiement);

                    // Activer le plan Premium pour 1 mois
                    Subscription subscription = paiement.getSubscription();
                    subscription.setPlan(SubscriptionPlan.PREMIUM);
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setDateActivation(LocalDate.now());
                    subscription.setDateExpiration(LocalDate.now().plusMonths(1));
                    subscription.setRenouvellementAuto(paiement.isRenouvellementAuto());
                    subscription.setMoyenPaiement(paiement.getMoyenPaiement());
                    subscriptionRepository.save(subscription);

                    // Notifier le prestataire par email
                    String formattedDate = subscription.getDateExpiration()
                            .format(DATE_FORMATTER);
                    emailService.sendSubscriptionPremiumNotification(
                            subscription.getProvider().getEmail(),
                            subscription.getProvider().getFirstName(),
                            subscription.getPlan().name(),
                            formattedDate
                    );

                    log.info("Premium activé via callback pour prestataire ID : {}",
                            subscription.getProvider().getId());

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Abonnement Premium activé avec succès"
                    ));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        Map.<String, Object>of(
                                "success", false,
                                "message", "Paiement abonnement introuvable pour la référence : " + ref
                        )
                ));
    }
}
