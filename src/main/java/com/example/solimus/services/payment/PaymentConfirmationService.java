package com.example.solimus.services.payment;

import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.repositories.*;
import com.example.solimus.services.admin.notification.AdminNotificationPreferenceService;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.provider.ProviderService;
import com.example.solimus.services.provider.wallet.WalletService;
import com.example.solimus.services.shared.StatusRecalculationService;
import com.example.solimus.utils.PasswordGeneratorUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmation des paiements (succès / échec) — seule source de vérité métier, quel que soit le
 * canal qui la déclenche : webhook TouchPay hosted (SolimusCallbackController), ou API TouchPay
 * Wave / Orange Money (MobileMoneyPaymentService). Le routage se fait par préfixe de référence
 * (PAY-, SOL-, SUB-, SYN-, SYR-, SYA-, PRA-, CPY-, ECP-) ; chaque traitement est idempotent
 * (un paiement déjà COMPLETED n'est jamais retraité).
 *
 * Logique déplacée telle quelle depuis SolimusCallbackController (aucun changement de règle).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmationService {

    private final PaymentRepository paymentRepository;
    private final InterventionRequestRepository interventionRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;
    private final ExceptionalCallItemRepository exceptionalCallItemRepository;
    private final SyndicWalletRepository syndicWalletRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final ProviderService providerService;
    private final WalletService walletService;
    private final ProviderWalletRepository providerWalletRepository;
    private final ProviderWalletTransactionRepository providerWalletTransactionRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final SyndicProfileRepository syndicProfileRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ActivityLogRepository activityLogRepository;
    private final NotificationService notificationService;
    private final AdminNotificationPreferenceService adminNotificationPreferenceService;
    private final PropertyRepository propertyRepository;
    private final StatusRecalculationService statusRecalculationService;
    private final UserRepository userRepository;

    // Formateur de date pour les emails de confirmation (ex: "01 Janvier 2026")
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);

    // QUAND : appelé par les 3 points d'entrée ci-dessus (webhook /callback, redirections
    // /payment-success et /payment-failed) dès qu'on a un résultat (succès ou échec) à traiter.
    // POURQUOI : un seul routeur pour dispatcher vers le bon traitement selon le préfixe de la
    // référence (PAY-, SUB-, SYN-...) — évite de dupliquer la logique de chaque cas 3 fois.
    @Transactional
    public ResponseEntity<Map<String, Object>> routeCallbackByPrefix(String ref, boolean succes) {

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
            // On route vers le traitement spécifique au renouvellement manuel par l'admin (syndic)
            return handleAdminSyndicRenewalCallback(ref, succes);
        }

        if (ref.startsWith("PRA-")) {
            // On route vers le traitement spécifique au renouvellement manuel par l'admin (prestataire)
            return handleAdminProviderRenewalCallback(ref, succes);
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
    // QUAND : référence commençant par PAY- ou SOL- — un copropriétaire paie l'acompte ou le
    // solde d'une demande de travaux.
    // POURQUOI : créditer le wallet du prestataire (argent externe reçu), mettre à jour les
    // montants de l'intervention, et clôturer l'intervention si c'est le solde qui vient d'être payé.
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleOwnerInterventionPaymentCallback(
            String ref, boolean succes) {

        // Ligne verrouillée : le contrôle "déjà COMPLETED" ci-dessous est fiable même si TouchPay
        // rejoue le callback en parallèle (webhook + redirection /payment-success)
        return paymentRepository.findByReferenceForUpdate(ref)
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

                    // Synchronisation financière de la demande d'intervention
                    InterventionRequest req = payment.getInterventionRequest();

                    // Le copropriétaire a payé par Mobile Money (argent externe) → le wallet du prestataire est
                    // crédité du montant payé, dans son grand livre, rattaché à l'intervention. Wallet créé à la
                    // volée s'il n'existe pas, et verrouillé (pas de crédit concurrent incohérent). Le paiement
                    // est déjà COMPLETED ci-dessus : un callback rejoué s'arrête au contrôle anti-doublon.
                    creditProviderForOwnerPayment(payment, req);
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

                    // Le solde clôture l'intervention (FINAL_VALIDATION) : recalcule la santé de la résidence
                    if (payment.getType() == PaymentType.SOLDE && req.getResidence() != null) {
                        statusRecalculationService.recalculateResidenceHealthStatus(req.getResidence());
                    }

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
    // QUAND : référence commençant par SUB- — un prestataire paie lui-même son abonnement
    // (self-service).
    // POURQUOI : activer l'abonnement, annuler tout ancien abonnement encore actif de ce
    // prestataire, notifier les admins (ce paiement est du revenu plateforme).
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleSubscriptionCallback(String ref, boolean succes) {

        // On retrouve la Subscription créée en PENDING grâce à sa référence unique
        return providerSubscriptionRepository.findByTransactionRef(ref)
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
                        // Le paiement a échoué côté TouchPay → on met à échec cette tentative
                        subscription.setStatus(SubscriptionStatus.FAILED);
                        subscription.setPaymentStatus(PaymentStatus.FAILED);
                        providerSubscriptionRepository.save(subscription);

                        notifyAdminsPaymentFailed(
                                resolveProviderLabel(subscription.getProvider()), subscription.getAmountPaid());

                        // On trace l'échec dans les logs pour debug
                        log.warn("Paiement abonnement échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement abonnement marqué comme échoué"
                        ));
                    }

                    // Le paiement est confirmé par TouchPay → on débloque réellement l'accès
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setPaymentStatus(PaymentStatus.COMPLETED);
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

                    // Notifie les admins : ce paiement d'abonnement constitue du revenu plateforme
                    notifyAdminsPaymentReceived(
                            resolveProviderLabel(subscription.getProvider()), subscription.getAmountPaid());

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
    // QUAND : référence commençant par SYN- — un admin crée un nouveau compte syndic et paie
    // son premier abonnement pour lui.
    // POURQUOI : c'est le SEUL moment où le compte syndic devient réellement actif — génère son
    // mot de passe temporaire, l'active, lui envoie ses identifiants par email, notifie les admins.
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

                        notifyAdminsPaymentFailed(
                                resolveSyndicLabel(subscription.getSyndic()), subscription.getAmountPaid());

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
                    // Flux actuel : le compte est créé ACTIVE et ses identifiants sont déjà partis par
                    // email à la création (SyndicServiceImpl.createSyndic) — on ne régénère surtout pas
                    // un mot de passe que le syndic utilise peut-être déjà. Seuls les comptes de l'ancien
                    // flux (PENDING, sans mot de passe) sont activés et reçoivent leurs identifiants ici.
                    boolean legacyPendingAccount = syndic.getStatus() == UserStatus.PENDING;
                    String temporaryPassword = null;
                    if (legacyPendingAccount) {
                        temporaryPassword = PasswordGeneratorUtil.generateTemporaryPassword();
                        syndic.setPassword(passwordEncoder.encode(temporaryPassword));
                        syndic.setStatus(UserStatus.ACTIVE);
                        // Sauvegarde explicite — ne pas se reposer uniquement sur le dirty-checking JPA
                        // pour un compte utilisateur au statut/mot de passe aussi critique
                        userRepository.save(syndic);
                    }

                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    // Posé une seule fois, ici, et jamais modifié ensuite — même si l'abonnement expire,
                    // est annulé ou désactivé plus tard, ce paiement précis restera "Payé" dans l'historique
                    subscription.setPaymentStatus(PaymentStatus.COMPLETED);
                    syndicSubscriptionRepository.save(subscription);

                    String companyName = syndicProfileRepository.findByUserId(syndic.getId())
                            .map(SyndicProfile::getCompanyName) //SI on trouve son profil on le map pour avoir le nom de l'entreprise
                            .orElse(null);

                    if (legacyPendingAccount) {
                        emailService.sendSyndicAccountCreated(
                                syndic.getEmail(),
                                temporaryPassword,
                                syndic.getFirstName(),
                                companyName);
                    } else {
                        emailService.sendEmail(
                                syndic.getEmail(),
                                "Abonnement Solimus activé",
                                "Bonjour " + syndic.getFirstName() + ",\n\n" +
                                        "Le paiement de votre abonnement \"" + subscription.getSyndicPlan().getName() +
                                        "\" a été confirmé. Il est actif jusqu'au " +
                                        subscription.getEndDate().format(DATE_FORMATTER) + ".\n\n" +
                                        "L'équipe SOLIMUS");
                    }

                    // Le compte syndic n'est réellement "créé" qu'à partir d'ici (paiement confirmé) —
                    // c'est donc le seul moment légitime pour déclencher l'événement NEW_SYNDIC_CREATED
                    adminNotificationPreferenceService.notifyAdmins(
                            AdminNotificationEventType.NEW_SYNDIC_CREATED,
                            "Nouveau syndic créé",
                            (companyName != null ? companyName : syndic.getFirstName() + " " + syndic.getLastName())
                                    + " a rejoint la plateforme.");

                    // Ce paiement d'abonnement constitue également du revenu plateforme
                    notifyAdminsPaymentReceived(
                            companyName != null ? companyName : syndic.getFirstName() + " " + syndic.getLastName(),
                            subscription.getAmountPaid());

                    log.info("Abonnement syndic {} activé pour {} — expire le {}",
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
    // QUAND : référence commençant par SYR- — un syndic déjà actif change lui-même de formule
    // d'abonnement (upgrade/downgrade).
    // POURQUOI : activer la nouvelle formule, annuler l'ancienne, confirmer par simple email
    // (contrairement à SYN-, le syndic est déjà actif — pas de nouveaux identifiants à envoyer).
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

                        notifyAdminsPaymentFailed(
                                resolveSyndicLabel(subscription.getSyndic()), subscription.getAmountPaid());

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

                    // Ce paiement de changement de formule constitue également du revenu plateforme
                    notifyAdminsPaymentReceived(resolveSyndicLabel(syndic), subscription.getAmountPaid());

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
    // QUAND : référence commençant par SYA- — un admin renouvelle manuellement l'abonnement
    // d'un syndic existant (ex: paiement reçu hors plateforme, renouvellement anticipé).
    // POURQUOI : activer le renouvellement, puis notifier/facturer selon les 2 cases cochées par
    // l'admin au moment de l'initiation (pas de comportement forcé, contrairement à SYN-/SYR-).
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

                        notifyAdminsPaymentFailed(
                                resolveSyndicLabel(subscription.getSyndic()), subscription.getAmountPaid());

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

                    // Ce paiement de renouvellement constitue également du revenu plateforme
                    notifyAdminsPaymentReceived(resolveSyndicLabel(syndic), subscription.getAmountPaid());

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
    // CAS 2e — Renouvellement manuel d'un abonnement prestataire par l'admin (PRA-)
    // QUAND : référence commençant par PRA- — même chose que SYA- mais côté prestataire.
    // POURQUOI : idem SYA-, adapté au prestataire (push au lieu d'email pour la notification).
    // =========================================================================
    private ResponseEntity<Map<String, Object>> handleAdminProviderRenewalCallback(String ref, boolean succes) {

        // On retrouve le nouvel abonnement créé en PENDING par PlanServiceImpl.renewProviderSubscription
        return providerSubscriptionRepository.findByTransactionRef(ref)
                .map(subscription -> {

                    // Anti-double callback, basé sur le résultat du paiement de CETTE tentative précise
                    if (subscription.getPaymentStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Renouvellement déjà confirmé"
                        ));
                    }
                    if (!succes) {
                        subscription.setStatus(SubscriptionStatus.FAILED);
                        subscription.setPaymentStatus(PaymentStatus.FAILED);
                        providerSubscriptionRepository.save(subscription);

                        notifyAdminsPaymentFailed(
                                resolveProviderLabel(subscription.getProvider()), subscription.getAmountPaid());

                        log.warn("Renouvellement prestataire (admin) échoué pour ref : {}", ref);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Renouvellement marqué comme échoué"
                        ));
                    }

                    // Le paiement est confirmé → le nouvel abonnement devient actif immédiatement
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setPaymentStatus(PaymentStatus.COMPLETED);
                    providerSubscriptionRepository.save(subscription);

                    User provider = subscription.getProvider();

                    // Un seul abonnement ACTIVE à la fois pour ce prestataire
                    List<ProviderSubscription> previousActive = providerSubscriptionRepository
                            .findActiveByProviderIdExcluding(provider.getId(), subscription.getId());
                    for (ProviderSubscription old : previousActive) {
                        old.setStatus(SubscriptionStatus.CANCELLED);
                    }
                    providerSubscriptionRepository.saveAll(previousActive);

                    // On respecte exactement les 2 cases cochées par l'admin au moment de l'initiation.
                    if (Boolean.TRUE.equals(subscription.getNotifyClient())) {
                        notificationService.sendPush(
                                provider.getId(),
                                "Abonnement renouvelé",
                                "Votre abonnement \"" + subscription.getProviderPlan().getName() +
                                        "\" a été renouvelé par un administrateur.");
                    }

                    if (Boolean.TRUE.equals(subscription.getSendInvoiceEmail())) {
                        emailService.sendEmail(
                                provider.getEmail(),
                                "Facture — Renouvellement de votre abonnement Solimus",
                                "Bonjour " + provider.getFirstName() + ",\n\n" +
                                        "Voici le récapitulatif de votre renouvellement :\n" +
                                        "Référence : " + ref + "\n" +
                                        "Formule : " + subscription.getProviderPlan().getName() + "\n" +
                                        "Montant : " + subscription.getAmountPaid() + " FCFA\n" +
                                        "Période : du " + subscription.getStartDate().format(DATE_FORMATTER) +
                                        " au " + subscription.getEndDate().format(DATE_FORMATTER) + "\n\n" +
                                        "Cordialement,\nL'équipe Solimus");
                    }

                    // Ce paiement de renouvellement constitue également du revenu plateforme
                    notifyAdminsPaymentReceived(resolveProviderLabel(provider), subscription.getAmountPaid());

                    log.info("Renouvellement prestataire (admin) {} confirmé pour {} — formule {} — expire le {}",
                            ref,
                            provider.getEmail(),
                            subscription.getProviderPlan().getName(),
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
    // QUAND : référence commençant par CPY- — un copropriétaire paie une charge courante
    // (trimestrielle/mensuelle) générée par le syndic. C'est le cas de Daouda Diallo.
    // POURQUOI : solder la charge (aucun paiement partiel autorisé), créditer le wallet du
    // syndic, recalculer les statuts (lot, santé résidence), notifier le syndic.
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

                    // Aucun paiement partiel autorisé : le montant reçu doit couvrir le reste dû
                    // actuel (quote-part + pénalité si appliquée entre-temps). Sinon on rejette —
                    // ni SyndicWalletTransaction, ni modification de paidAmount/status.
                    ChargeCallItem item = paiement.getChargeCallItem();
                    BigDecimal montantRestantDu = item.getRemainingAmount();
                    if (paiement.getAmount().compareTo(montantRestantDu) < 0) {
                        paiement.setStatus(PaymentStatus.FAILED);
                        chargeCallPaymentRepository.save(paiement);
                        log.warn("Paiement charge {} rejeté : montant insuffisant ({} FCFA reçu, {} FCFA attendu)",
                                ref, paiement.getAmount(), montantRestantDu);
                        return ResponseEntity.badRequest().body(Map.<String, Object>of(
                                "success", false,
                                "message", "Montant insuffisant : " + montantRestantDu + " FCFA attendu, "
                                        + paiement.getAmount() + " FCFA reçu"
                        ));
                    }

                    // Confirmer le paiement
                    paiement.setStatus(PaymentStatus.COMPLETED);
                    paiement.setPaidAt(LocalDateTime.now());
                    chargeCallPaymentRepository.save(paiement);

                    // Met à jour la ligne de charge : toujours payée intégralement désormais
                    item.setPaidAmount(item.getPaidAmount().add(paiement.getAmount()));
                    item.setStatus(ChargeItemPaymentStatus.PAID);
                    chargeCallItemRepository.save(item);

                    // Recalcule les statuts affichés impactés par ce paiement : le(s) lot(s) du
                    // copropriétaire (displayStatus) et la santé de la résidence (healthStatus)
                    Residence residence = item.getChargeCall().getBudget().getResidence();
                    propertyRepository.findByOwnerIdAndResidenceId(paiement.getOwner().getId(), residence.getId())
                            .forEach(statusRecalculationService::recalculatePropertyDisplayStatus);
                    statusRecalculationService.recalculateResidenceHealthStatus(residence);

                    // Crédite le wallet du syndic (catégorie CHARGES) — créé à la volée s'il n'existe
                    // pas encore (même logique que partout ailleurs dans l'app, ex: getWalletBalance),
                    // pour ne jamais faire échouer la confirmation d'un paiement déjà réellement reçu
                    SyndicWallet syndicWallet = syndicWalletRepository
                            .findBySyndicId(residence.getSyndic().getId())
                            .orElseGet(() -> syndicWalletRepository.save(
                                    SyndicWallet.builder().syndic(residence.getSyndic()).build()));

                    SyndicWalletTransaction transaction = new SyndicWalletTransaction();
                    transaction.setWallet(syndicWallet);
                    transaction.setResidence(residence);
                    transaction.setCoOwner(paiement.getOwner());
                    transaction.setCategory(WalletTransactionCategory.CHARGES);
                    transaction.setAmount(paiement.getAmount());
                    transaction.setLabel("Charge courante — " + buildSimplePeriodeLabel(item.getChargeCall()) + " " + item.getChargeCall().getYear());
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
    // QUAND : référence commençant par ECP- — un copropriétaire paie une charge exceptionnelle
    // (travaux votés en AG, par exemple).
    // POURQUOI : identique au CAS 3, adapté aux charges exceptionnelles.
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

                    // Aucun paiement partiel autorisé : le montant reçu doit couvrir le reste dû actuel
                    ExceptionalCallItem item = paiement.getExceptionalCallItem();
                    BigDecimal montantRestantDu = item.getQuotePart().subtract(
                            item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO);
                    if (paiement.getAmount().compareTo(montantRestantDu) < 0) {
                        paiement.setStatus(PaymentStatus.FAILED);
                        exceptionalCallPaymentRepository.save(paiement);
                        log.warn("Paiement charge exceptionnelle {} rejeté : montant insuffisant ({} FCFA reçu, {} FCFA attendu)",
                                ref, paiement.getAmount(), montantRestantDu);
                        return ResponseEntity.badRequest().body(Map.<String, Object>of(
                                "success", false,
                                "message", "Montant insuffisant : " + montantRestantDu + " FCFA attendu, "
                                        + paiement.getAmount() + " FCFA reçu"
                        ));
                    }

                    // Confirmer le paiement
                    paiement.setStatus(PaymentStatus.COMPLETED);
                    paiement.setPaidAt(LocalDateTime.now());
                    exceptionalCallPaymentRepository.save(paiement);

                    // Met à jour la ligne d'appel exceptionnel : toujours payée intégralement désormais
                    item.setPaidAmount(item.getPaidAmount().add(paiement.getAmount()));
                    item.setStatus(ChargeItemPaymentStatus.PAID);
                    exceptionalCallItemRepository.save(item);

                    // Crédite le wallet du syndic (catégorie CHARGES) — créé à la volée s'il n'existe
                    // pas encore (même logique que partout ailleurs dans l'app, ex: getWalletBalance),
                    // pour ne jamais faire échouer la confirmation d'un paiement déjà réellement reçu
                    Residence residence = item.getExceptionalCall().getResidence();
                    SyndicWallet syndicWallet = syndicWalletRepository
                            .findBySyndicId(residence.getSyndic().getId())
                            .orElseGet(() -> syndicWalletRepository.save(
                                    SyndicWallet.builder().syndic(residence.getSyndic()).build()));

                    SyndicWalletTransaction transaction = new SyndicWalletTransaction();
                    transaction.setWallet(syndicWallet);
                    transaction.setResidence(residence);
                    transaction.setCoOwner(paiement.getOwner());
                    transaction.setCategory(WalletTransactionCategory.CHARGES);
                    transaction.setAmount(paiement.getAmount());
                    transaction.setLabel("Charge exceptionnelle — " + item.getExceptionalCall().getTitle());
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

    // Crédit du wallet prestataire après confirmation d'un acompte (PAY-) ou d'un solde (SOL-) payé par
    // le copropriétaire (TouchPay hosted ou Wave / Orange Money via GU) — même grand livre que les
    // paiements du syndic (catégorie INTERVENTION_PAYMENT = argent mobile money reçu)
    private void creditProviderForOwnerPayment(PaymentProvider payment, InterventionRequest req) {
        User provider = payment.getProvider();
        ProviderWallet wallet = providerWalletRepository.findByProviderIdForUpdate(provider.getId())
                .orElseGet(() -> providerWalletRepository.save(ProviderWallet.builder().provider(provider).build()));

        String kind = payment.getType() == PaymentType.ACOMPTE ? "Acompte" : "Solde final";
        ProviderWalletTransaction credit = new ProviderWalletTransaction();
        credit.setWallet(wallet);
        credit.setCategory(ProviderWalletTransactionCategory.INTERVENTION_PAYMENT);
        credit.setAmount(payment.getAmount());
        credit.setLabel(kind + " — " + (req != null ? req.getTitle() : payment.getReference()));
        credit.setInterventionRequest(req);
        credit.setTransactionDate(LocalDateTime.now());
        providerWalletTransactionRepository.save(credit);

        notificationService.sendPush(provider.getId(), kind + " reçu",
                payment.getAmount() + " FCFA crédités sur votre wallet"
                        + (req != null ? " pour \"" + req.getTitle() + "\"" : "") + ".");
        log.info("Wallet prestataire {} crédité de {} FCFA ({} {})", provider.getId(), payment.getAmount(),
                kind, payment.getReference());
    }

    // =========================================================================
    // QUAND : appelé par les 5 callbacks d'abonnement (SUB-, SYN-, SYR-, SYA-, PRA-) sur succès.
    // POURQUOI : seuls ces paiements constituent du revenu pour la plateforme (contrairement aux
    // paiements de charges CPY-/ECP-, qui ne font que transiter vers le wallet du syndic).
    // =========================================================================
    private void notifyAdminsPaymentReceived(String payerLabel, BigDecimal amount) {
        adminNotificationPreferenceService.notifyAdmins(
                AdminNotificationEventType.PAYMENT_RECEIVED,
                "Paiement reçu",
                payerLabel + " a payé " + amount + " FCFA.");
    }

    // QUAND : appelé par les mêmes 5 callbacks d'abonnement, sur la branche "!succes".
    // POURQUOI : même périmètre que notifyAdminsPaymentReceived (revenu plateforme uniquement).
    private void notifyAdminsPaymentFailed(String payerLabel, BigDecimal amount) {
        adminNotificationPreferenceService.notifyAdmins(
                AdminNotificationEventType.PAYMENT_FAILED,
                "Paiement échoué",
                "Le paiement de " + amount + " FCFA initié par " + payerLabel + " a échoué.");
    }

    // Nom de société du syndic si son profil en a un, sinon prénom + nom
    private String resolveSyndicLabel(User syndic) {
        return syndicProfileRepository.findByUserId(syndic.getId())
                .map(SyndicProfile::getCompanyName)
                .orElse(syndic.getFirstName() + " " + syndic.getLastName());
    }

    // Nom de société du prestataire si son profil en a un, sinon prénom + nom
    private String resolveProviderLabel(User provider) {
        return providerProfileRepository.findByUser(provider)
                .map(ProviderProfile::getCompanyName)
                .orElse(provider.getFirstName() + " " + provider.getLastName());
    }

    // Libellé court de la période d'un appel de charges, ex: "T3" (trimestriel) ou "Jan" (mensuel)
    private String buildSimplePeriodeLabel(ChargeCall chargeCall) {
        if (chargeCall.getFrequency() == ChargeFrequency.TRIMESTRIEL) {
            return "T" + chargeCall.getPeriodNumber();
        }
        String[] mois = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        int index = chargeCall.getPeriodNumber() - 1;
        return (index >= 0 && index < mois.length) ? mois[index] : "P" + chargeCall.getPeriodNumber();
    }
}
