package com.example.solimus.controllers;

import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.provider.ProviderService;
import com.example.solimus.services.provider.wallet.WalletService;
import com.example.solimus.utils.PasswordGeneratorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private final ProviderSubscriptionRepository providerSubscriptionRepository;

    // Repository des abonnements syndics : SYN-*
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;

    // Repository des profils société syndic, pour récupérer le nom de société à l'activation
    private final SyndicProfileRepository syndicProfileRepository;

    // Encodeur utilisé pour chiffrer le mot de passe temporaire généré au moment de l'activation du syndic
    private final PasswordEncoder passwordEncoder;

    // Service email pour notifier le prestataire après activation Premium, et le syndic après activation de son compte
    private final EmailService emailService;

    // Repository des logs d'activité pour tracer les paiements
    private final ActivityLogRepository activityLogRepository;

    // Service de notifications push, pour alerter le syndic des nouveaux paiements
    private final NotificationService notificationService;

    // URL de la page "Paramètres" de l'app web Angular (espace syndic uniquement pour l'instant) —
    // destination des paiements self-service (SYR-). Les autres références (mobile, ou web admin
    // type SYN-/SYA- en attendant leur propre URL) continuent de rouvrir l'app via solimus://...
    @Value("${app.syndic-web-app-redirect-url}")
    private String syndicWebAppRedirectUrl;

    // Formateur de date pour l'email de confirmation Premium (ex: "01 Janvier 2026")
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);

    // =========================================================================
    // Routes de redirection TouchPay — confirment le paiement directement
    // côté serveur, avant même d'afficher la page. Contrairement à un fichier
    // HTML statique, ça fonctionne même si la WebView mobile ferme la page
    // juste après le chargement.
    // =========================================================================

    @GetMapping(value = "/redirect-success", produces = "text/html")
    public String redirectPaymentSuccess(@RequestParam("num_command") String reference) {

        // Identifie le type de paiement via le prefixe de la référence,
        // car TouchPay n'utilise qu'une seule URL de redirection pour tous les types
        if (reference.startsWith("CPY-")) {
            handleChargePaymentCallback(reference, true);
        } else if (reference.startsWith("ECP-")) {
            handleExceptionalChargePaymentCallback(reference, true);
        } else if (reference.startsWith("SUB-")) {
            handleSubscriptionCallback(reference, true);
        } else if (reference.startsWith("SYN-")) {
            handleSyndicSubscriptionCallback(reference, true);
        } else if (reference.startsWith("SYR-")) {
            handleSyndicPlanChangeCallback(reference, true);
        } else if (reference.startsWith("SYA-")) {
            handleAdminSyndicRenewalCallback(reference, true);
        } else if (reference.startsWith("PAY-") || reference.startsWith("SOL-")) {
            handleOwnerInterventionPaymentCallback(reference, true);
        }

        // SYR- vient du self-service syndic sur le web : on renvoie vers l'app Angular, pas vers le
        // lien profond mobile (qui échouerait silencieusement dans un navigateur)
        if (reference.startsWith("SYR-")) {
            String webRedirect = syndicWebAppRedirectUrl + "?paymentStatus=success&ref=" + reference;
            return "<html><body>" +
                    "<script>window.location.href = '" + webRedirect + "';</script>" +
                    "</body></html>";
        }

        return "<html><body style=\"text-align:center; font-family:sans-serif; margin-top:50px;\">" +
                "<h1 style=\"color:green;\">Paiement réussi</h1>" +
                "<p>Votre paiement a bien été confirmé.</p>" +
                "<p><a href=\"solimus://payment/success?ref=" + reference + "\">Retourner à l'application</a></p>" +
                "<script>" +
                "  window.location.href = 'solimus://payment/success?ref=" + reference + "';" +
                "</script>" +
                "</body></html>";
    }

    @GetMapping(value = "/redirect-failed", produces = "text/html")
    public String redirectPaymentFailed(@RequestParam("num_command") String reference) {

        // Identifie le type de paiement via le prefixe de la reference,
        // car TouchPay n'utilise qu'une seule URL de redirection pour tous les types
        if (reference.startsWith("CPY-")) {
            handleChargePaymentCallback(reference, false);
        } else if (reference.startsWith("ECP-")) {
            handleExceptionalChargePaymentCallback(reference, false);
        } else if (reference.startsWith("SUB-")) {
            handleSubscriptionCallback(reference, false);
        } else if (reference.startsWith("SYN-")) {
            handleSyndicSubscriptionCallback(reference, false);
        } else if (reference.startsWith("SYR-")) {
            handleSyndicPlanChangeCallback(reference, false);
        } else if (reference.startsWith("SYA-")) {
            handleAdminSyndicRenewalCallback(reference, false);
        } else if (reference.startsWith("PAY-") || reference.startsWith("SOL-")) {
            handleOwnerInterventionPaymentCallback(reference, false);
        }

        // Même logique que pour le succès : SYR- retourne vers l'app Angular
        if (reference.startsWith("SYR-")) {
            String webRedirect = syndicWebAppRedirectUrl + "?paymentStatus=failed&ref=" + reference;
            return "<html><body>" +
                    "<script>window.location.href = '" + webRedirect + "';</script>" +
                    "</body></html>";
        }

        return "<html><body style=\"text-align:center; font-family:sans-serif; margin-top:50px;\">" +
                "<h1 style=\"color:red;\">Paiement échoué</h1>" +
                "<p>Le paiement a été marqué comme échoué.</p>" +
                "<p><a href=\"solimus://payment/failed?ref=" + reference + "\">Retourner à l'application</a></p>" +
                "<script>" +
                "  window.location.href = 'solimus://payment/failed?ref=" + reference + "';" +
                "</script>" +
                "</body></html>";
    }

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

        if (ref.startsWith("SYN-")) {
            // On route vers le traitement spécifique à l'abonnement syndic
            return handleSyndicSubscriptionCallback(ref, succes);
        }

        if (ref.startsWith("SYR-")) {
            // On route vers le traitement spécifique au changement de formule syndic (self-service)
            return handleSyndicPlanChangeCallback(ref, succes);
        }

        if (ref.startsWith("SYA-")) {
            // On route vers le traitement spécifique au renouvellement manuel par l'admin
            return handleAdminSyndicRenewalCallback(ref, succes);
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
        return providerSubscriptionRepository.findByTransactionRef(ref)
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
                        providerSubscriptionRepository.save(subscription);

                        // On trace l'échec dans les logs pour debug
                        log.warn("Paiement abonnement échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement abonnement marqué comme échoué"
                        ));
                    }

                    // Le paiement est confirmé par TouchPay → on débloque réellement l'accès
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    providerSubscriptionRepository.save(subscription);

                    // Un seul abonnement ACTIVE à la fois pour ce prestataire — on annule l'éventuel
                    // ancien abonnement encore actif (ex: deux paiements initiés en parallèle avant
                    // que le premier n'ait eu le temps d'être confirmé)
                    List<ProviderSubscription> previousActive = providerSubscriptionRepository
                            .findActiveByProviderIdExcluding(subscription.getProvider().getId(), subscription.getId());
                    for (ProviderSubscription old : previousActive) {
                        old.setStatus(SubscriptionStatus.CANCELLED);
                    }
                    providerSubscriptionRepository.saveAll(previousActive);

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
    // CAS 2b — Paiement abonnement syndic à la création du compte (SYN-)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleSyndicSubscriptionCallback(String ref, boolean succes) {

        // On retrouve l'abonnement créé en PENDING par SyndicServiceImpl.createSyndic
        return syndicSubscriptionRepository.findByTransactionRef(ref)
                .map(subscription -> {

                    // Anti-double callback : basé sur le résultat du PAIEMENT (jamais retouché ensuite),
                    // pas sur le statut de l'abonnement qui, lui, peut évoluer plus tard pour d'autres raisons
                    if (subscription.getPaymentStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Abonnement déjà activé"
                        ));
                    }

                    if (!succes) {
                        // Le paiement a échoué côté TouchPay → le compte syndic reste PENDING
                        // (jamais activé) et sera purgé automatiquement par le scheduler
                        subscription.setStatus(SubscriptionStatus.FAILED);
                        subscription.setPaymentStatus(PaymentStatus.FAILED);
                        syndicSubscriptionRepository.save(subscription);

                        log.warn("Paiement abonnement syndic échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement abonnement syndic marqué comme échoué"
                        ));
                    }

                    // Le paiement est confirmé par TouchPay → on active réellement le compte syndic.
                    // Le mot de passe temporaire n'existe qu'à partir de maintenant : c'est le seul
                    // moment où le syndic a réellement payé, donc le seul moment légitime pour lui
                    // donner accès et lui envoyer ses identifiants.
                    User syndic = subscription.getSyndic();
                    String temporaryPassword = PasswordGeneratorUtil.generateTemporaryPassword();
                    syndic.setPassword(passwordEncoder.encode(temporaryPassword));
                    syndic.setStatus(UserStatus.ACTIVE);

                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    // Posé une seule fois, ici, et jamais modifié ensuite — même si l'abonnement expire,
                    // est annulé ou désactivé plus tard, ce paiement précis restera "Payé" dans l'historique
                    subscription.setPaymentStatus(PaymentStatus.COMPLETED);
                    syndicSubscriptionRepository.save(subscription);

                    String companyName = syndicProfileRepository.findByUserId(syndic.getId())
                            .map(SyndicProfile::getCompanyName) //SI on trouve son profil on le map pour avoir le nom de l'entreprise
                            .orElse(null);

                    emailService.sendSyndicAccountCreated(
                            syndic.getEmail(),
                            temporaryPassword,
                            syndic.getFirstName(),
                            companyName);

                    log.info("Abonnement syndic {} activé pour {} — expire le {} — identifiants envoyés par email",
                            ref,
                            syndic.getEmail(),
                            subscription.getEndDate().format(DATE_FORMATTER));

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Abonnement syndic activé avec succès"
                    ));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        Map.<String, Object>of(
                                "success", false,
                                "message", "Abonnement syndic introuvable pour la référence : " + ref
                        )
                ));
    }

    // =========================================================================
    // CAS 2c — Changement de formule syndic en self-service (SYR-)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleSyndicPlanChangeCallback(String ref, boolean succes) {

        // On retrouve le nouvel abonnement créé en PENDING par SyndicSubscriptionServiceImpl.initiateChangePlan
        return syndicSubscriptionRepository.findByTransactionRef(ref)
                .map(subscription -> {

                    // Anti-double callback, basé sur le résultat du paiement de CETTE tentative précise
                    if (subscription.getPaymentStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Changement de formule déjà confirmé"
                        ));
                    }

                    if (!succes) {
                        // Le paiement a échoué → la nouvelle formule n'est jamais activée,
                        // l'abonnement en cours du syndic (s'il y en a un) n'est pas touché
                        subscription.setStatus(SubscriptionStatus.FAILED);
                        subscription.setPaymentStatus(PaymentStatus.FAILED);
                        syndicSubscriptionRepository.save(subscription);

                        log.warn("Changement de formule syndic échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Changement de formule marqué comme échoué"
                        ));
                    }

                    // Le paiement est confirmé → la nouvelle formule devient active immédiatement.
                    // Contrairement à SYN- (création de compte), le syndic est déjà actif : on ne touche
                    // ni à son mot de passe ni à son statut, on n'envoie pas l'email "compte créé"
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setPaymentStatus(PaymentStatus.COMPLETED);
                    syndicSubscriptionRepository.save(subscription);

                    User syndic = subscription.getSyndic();

                    // On annule l'éventuel ancien abonnement encore actif de ce syndic — la nouvelle
                    // formule le remplace immédiatement, pas de chevauchement. Son paymentStatus reste
                    // COMPLETED pour toujours : il a bel et bien été payé en son temps, seul son cycle
                    // de vie s'arrête ici prématurément
                    //On cherche les Abonnement(s) encore ACTIVE d'un syndic, autre que celui-ci — utilisé au changement de formule
                    // (SYR-) pour annuler l'ancien abonnement remplacé par le nouveau
                    List<SyndicSubscription> previousActive = syndicSubscriptionRepository
                            .findActiveBySyndicIdExcluding(syndic.getId(), subscription.getId());
                    //une fois trouvé on change le statut
                    for (SyndicSubscription old : previousActive) {
                        old.setStatus(SubscriptionStatus.CANCELLED);
                    }
                    syndicSubscriptionRepository.saveAll(previousActive);

                    // Confirmation simple par email — pas de nouveaux identifiants à envoyer ici
                    emailService.sendEmail(
                            syndic.getEmail(),
                            "Changement de formule confirmé",
                            "Bonjour " + syndic.getFirstName() + ",\n\n" +
                                    "Votre changement de formule vers \"" + subscription.getSyndicPlan().getName() +
                                    "\" a bien été confirmé. Votre nouvel abonnement est actif jusqu'au " +
                                    subscription.getEndDate().format(DATE_FORMATTER) + ".\n\n" +
                                    "L'équipe SOLIMUS");

                    log.info("Changement de formule {} confirmé pour {} — nouvelle formule {} — expire le {}",
                            ref,
                            syndic.getEmail(),
                            subscription.getSyndicPlan().getName(),
                            subscription.getEndDate().format(DATE_FORMATTER));

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Changement de formule confirmé avec succès"
                    ));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        Map.<String, Object>of(
                                "success", false,
                                "message", "Changement de formule introuvable pour la référence : " + ref
                        )
                ));
    }

    // =========================================================================
    // CAS 2d — Renouvellement manuel d'un abonnement syndic par l'admin (SYA-)
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleAdminSyndicRenewalCallback(String ref, boolean succes) {

        // On retrouve le nouvel abonnement créé en PENDING par PlanServiceImpl.renewSyndicSubscription
        return syndicSubscriptionRepository.findByTransactionRef(ref)
                .map(subscription -> {

                    // Anti-double callback, basé sur le résultat du paiement de CETTE tentative précise
                    if (subscription.getPaymentStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Renouvellement déjà confirmé"
                        ));
                    }

                    if (!succes) {
                        // Le paiement a échoué → le renouvellement n'est jamais activé, l'abonnement
                        // en cours du syndic (s'il y en a un) n'est pas touché
                        subscription.setStatus(SubscriptionStatus.FAILED);
                        subscription.setPaymentStatus(PaymentStatus.FAILED);
                        syndicSubscriptionRepository.save(subscription);

                        log.warn("Renouvellement syndic (admin) échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Renouvellement marqué comme échoué"
                        ));
                    }

                    // Sinon -> Le paiement est confirmé → le nouvel abonnement devient actif immédiatement.
                    // Comme SYR-, on ne touche ni au mot de passe ni au statut du compte syndic
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setPaymentStatus(PaymentStatus.COMPLETED);
                    syndicSubscriptionRepository.save(subscription);

                    User syndic = subscription.getSyndic();

                    // Même règle que SYR- : un seul abonnement ACTIVE à la fois pour ce syndic
                    List<SyndicSubscription> previousActive = syndicSubscriptionRepository
                            .findActiveBySyndicIdExcluding(syndic.getId(), subscription.getId());
                    for (SyndicSubscription old : previousActive) {
                        old.setStatus(SubscriptionStatus.CANCELLED);
                    }
                    syndicSubscriptionRepository.saveAll(previousActive);

                    // On respecte exactement les 2 cases cochées par l'admin au moment de l'initiation
                    // (pas de comportement forcé, contrairement à SYN-/SYR- qui envoient toujours un email)
                    if (Boolean.TRUE.equals(subscription.getNotifyClient())) {
                        notificationService.sendNewPaymentNotification(
                                syndic.getId(),
                                "Abonnement renouvelé",
                                "Votre abonnement \"" + subscription.getSyndicPlan().getName() +
                                        "\" a été renouvelé par un administrateur.");
                    }

                    if (Boolean.TRUE.equals(subscription.getSendInvoiceEmail())) {
                        emailService.sendEmail(
                                syndic.getEmail(),
                                "Facture — Renouvellement de votre abonnement Solimus",
                                "Bonjour " + syndic.getFirstName() + ",\n\n" +
                                        "Voici le récapitulatif de votre renouvellement :\n" +
                                        "Référence : " + ref + "\n" +
                                        "Formule : " + subscription.getSyndicPlan().getName() + "\n" +
                                        "Montant : " + subscription.getAmountPaid() + " FCFA\n" +
                                        "Période : du " + subscription.getStartDate().format(DATE_FORMATTER) +
                                        " au " + subscription.getEndDate().format(DATE_FORMATTER) + "\n\n" +
                                        "Cordialement,\nL'équipe Solimus");
                    }

                    log.info("Renouvellement syndic (admin) {} confirmé pour {} — formule {} — expire le {}",
                            ref,
                            syndic.getEmail(),
                            subscription.getSyndicPlan().getName(),
                            subscription.getEndDate().format(DATE_FORMATTER));

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Renouvellement confirmé avec succès"
                    ));
                })
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

                    // Statut posé explicitement ICI, au moment du paiement réellement confirmé —
                    // jamais recalculé ailleurs à l'affichage
                    if (item.getPaidAmount().compareTo(item.getQuotePart()) >= 0) {
                        item.setStatus(ChargeItemPaymentStatus.PAID);
                    } else {
                        item.setStatus(ChargeItemPaymentStatus.PARTIALLY_PAID);
                    }

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

                    // Alerte push le syndic (si sa préférence "Nouveaux paiements" est activée)
                    notificationService.sendNewPaymentNotification(
                            residence.getSyndic().getId(),
                            "Nouveau paiement reçu",
                            paiement.getOwner().getFirstName() + " " + paiement.getOwner().getLastName() +
                                    " a payé " + paiement.getAmount() + " FCFA — " + item.getReference()
                    );

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

                    // Statut posé explicitement ICI, au moment du paiement réellement confirmé —
                    // jamais recalculé ailleurs à l'affichage
                    if (item.getPaidAmount().compareTo(item.getQuotePart()) >= 0) {
                        item.setStatus(ChargeItemPaymentStatus.PAID);
                    } else {
                        item.setStatus(ChargeItemPaymentStatus.PARTIALLY_PAID);
                    }

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

                    // Alerte push le syndic (si sa préférence "Nouveaux paiements" est activée)
                    notificationService.sendNewPaymentNotification(
                            residence.getSyndic().getId(),
                            "Nouveau paiement reçu",
                            paiement.getOwner().getFirstName() + " " + paiement.getOwner().getLastName() +
                                    " a payé " + paiement.getAmount() + " FCFA — " + item.getExceptionalCall().getTitle()
                    );

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