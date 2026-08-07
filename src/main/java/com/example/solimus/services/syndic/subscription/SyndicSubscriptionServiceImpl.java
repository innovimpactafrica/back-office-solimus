package com.example.solimus.services.syndic.subscription;

import com.example.solimus.dtos.syndic.subscription.InitiateSyndicPlanChangeDTO;
import com.example.solimus.dtos.syndic.subscription.MySyndicSubscriptionDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanChangeResponseDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanOptionDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicSubscriptionHistoryDTO;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.AdminNotificationEventType;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionDuration;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.admin.notification.AdminNotificationPreferenceService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicSubscriptionServiceImpl implements SyndicSubscriptionService {

    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final SyndicPlanRepository syndicPlanRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SyndicProfileRepository syndicProfileRepository;
    private final AdminNotificationPreferenceService adminNotificationPreferenceService;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    @Override
    @Transactional(readOnly = true)
    public MySyndicSubscriptionDTO getMySubscription() {

        // Le syndic connecté ne peut consulter que son propre abonnement
        User currentSyndic = getCurrentUser();

        // On affiche en priorité l'abonnement réellement ACTIVE — jamais "le plus récent par date
        // de fin", qui peut ramener un abonnement annulé (ex: un ancien abonnement annuel remplacé
        // par un nouveau mensuel finit "plus tard" alors qu'il n'est plus actif). On ne retombe sur
        // le plus récent, tous statuts confondus, que si aucun abonnement n'est actif du tout —
        // pour quand même afficher quelque chose (ex: "Expiré", "Annulé").
        SyndicSubscription subscription = syndicSubscriptionRepository
                .findFirstBySyndicIdAndStatus(currentSyndic.getId(), SubscriptionStatus.ACTIVE)
                .filter(SyndicSubscription::isCurrentlyActive)
                .or(() -> syndicSubscriptionRepository.findFirstBySyndicIdOrderByEndDateDesc(currentSyndic.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun abonnement trouvé pour ce compte"));

        SyndicPlan plan = subscription.getSyndicPlan();

        return MySyndicSubscriptionDTO.builder()
                .planName(plan.getName())
                .description(plan.getDescription())
                .active(subscription.isCurrentlyActive())
                .statusLabel(mapStatusToLabel(subscription.getStatus()))
                .features(plan.getFeatures().stream().map(SyndicPlanFeature::getLabel).toList())
                .featureCodes(plan.getFeatures().stream().map(feature -> feature.name()).toList())
                .maxResidences(plan.getMaxResidences())
                .maxApartments(plan.getMaxApartments())
                .activationDate(subscription.getStartDate())
                .expirationDate(subscription.getEndDate())
                .monthlyAmount(plan.getMonthlyPrice())
                .yearlyAmount(plan.getYearlyPrice())
                .nextRenewal(subscription.getDuration() == SubscriptionDuration.MONTHLY
                        ? "Manuel — tous les mois"
                        : "Manuel — tous les 12 mois")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SyndicSubscriptionHistoryDTO> getPaymentHistory(int page, int size) {

        // Le syndic connecté ne peut consulter que son propre historique
        User currentSyndic = getCurrentUser();

        // Toutes ses tentatives d'abonnement (payées, échouées ou en attente), du plus récent au plus ancien
        Pageable pageable = PageRequest.of(page, size);
        Page<SyndicSubscription> subscriptions = syndicSubscriptionRepository
                .findBySyndicIdOrderByCreatedAtDesc(currentSyndic.getId(), pageable);

        return subscriptions.map(subscription -> SyndicSubscriptionHistoryDTO.builder()
                .date(subscription.getCreatedAt())
                .planName(subscription.getSyndicPlan().getName())
                .amount(subscription.getAmountPaid())
                .paymentMethodLabel(subscription.getMethod() != null
                        ? subscription.getMethod().getLabel()
                        : null)
                .statusLabel(mapPaymentStatusToLabel(subscription.getPaymentStatus()))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyndicPlanOptionDTO> listAvailablePlans() {

        // Seules les formules actives sont proposables au choix — celles désactivées par l'admin
        // ne doivent plus apparaître dans la modale, même si un syndic y est déjà abonné
        return syndicPlanRepository.findByActiveTrue().stream()
                .map(plan -> SyndicPlanOptionDTO.builder()
                        .id(plan.getId())
                        .name(plan.getName())
                        .monthlyPrice(plan.getMonthlyPrice())
                        .yearlyPrice(plan.getYearlyPrice())
                        .features(plan.getFeatures().stream().map(SyndicPlanFeature::getLabel).toList())
                        .maxResidences(plan.getMaxResidences())
                        .maxApartments(plan.getMaxApartments())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public SyndicPlanChangeResponseDTO initiateChangePlan(InitiateSyndicPlanChangeDTO dto) {

        // 1. Le syndic connecté est celui qui paie ici, en self-service — pas l'admin
        User currentSyndic = getCurrentUser();

        // Double sécurité : ne fait jamais confiance uniquement au @PreAuthorize du contrôleur —
        // vérifie explicitement ici aussi, pour ne jamais recréer un abonnement syndic sur un
        // compte qui n'a pas ce rôle, même si la sécurité au niveau des routes est un jour mal configurée
        if (currentSyndic.getRole().getName() != ERole.ROLE_SYNDIC) {
            throw new BadRequestException("Seul un compte syndic peut souscrire à cet abonnement");
        }

        // Bloque une nouvelle tentative tant qu'une autre est déjà en attente de confirmation —
        // évite d'empiler des paiements PENDING en boucle. Il faut attendre soit la confirmation
        // TouchPay, soit l'expiration automatique (5 min, failStalePendingSyndicSubscriptions)
        if (syndicSubscriptionRepository.existsBySyndicIdAndStatus(currentSyndic.getId(), SubscriptionStatus.PENDING)) {
            throw new BadRequestException(
                    "Un paiement est déjà en attente de confirmation. Veuillez patienter quelques minutes avant de réessayer.");
        }

        // 2. Récupère la formule choisie et vérifie qu'elle est toujours proposée
        SyndicPlan plan = syndicPlanRepository.findById(dto.getSyndicPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Formule d'abonnement introuvable"));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new BadRequestException("Cette formule n'est plus disponible");
        }

        // 3. Détermine le montant selon la durée choisie, et vérifie que ce tarif est bien configuré
        BigDecimal amount = dto.getDuration() == SubscriptionDuration.YEARLY
                ? plan.getYearlyPrice()
                : plan.getMonthlyPrice();

        if (amount == null) {
            throw new BadRequestException(
                    dto.getDuration() == SubscriptionDuration.YEARLY
                            ? "Cette formule ne propose pas de tarif annuel"
                            : "Cette formule ne propose pas de tarif mensuel");
        }

        // 4. Référence unique préfixée SYR- (self-service), pour que le bridge et le callback la
        // distinguent de SYN- (création par l'admin) — le callback SYR- ne doit ni régénérer de mot
        // de passe ni renvoyer l'email "compte créé", puisque ce syndic est déjà actif
        String transactionRef = generateReference("SYR");

        // 5. Détermine la date de départ de la nouvelle période :
        // - Renouvellement (même formule, encore active) → prolonge à partir de la date de fin actuelle,
        //   pour ne jamais faire perdre les jours restants déjà payés
        // - Changement vers une autre formule, ou ancien abonnement déjà expiré → démarre maintenant

        LocalDateTime now = LocalDateTime.now();

        // Recherche le dernier abonnement du syndic
        LocalDateTime startDate = syndicSubscriptionRepository
                .findFirstBySyndicIdOrderByEndDateDesc(currentSyndic.getId())

                // Vérifie que cet abonnement est toujours actif
                .filter(SyndicSubscription::isCurrentlyActive)

                // Vérifie que c'est le même plan que celui sélectionné
                .filter(current -> current.getSyndicPlan().getId().equals(plan.getId()))

                // Si c'est le même plan, récupère sa date de fin
                .map(SyndicSubscription::getEndDate)

                // Sinon, utilise la date actuelle comme date de début du nouvel abonnement
                .orElse(now);

        // Crée le nouvel abonnement en PENDING — il ne devient ACTIVE, et ne remplace l'ancien,
        // qu'à la confirmation réelle du paiement (callback TouchPay)
        LocalDateTime endDate = startDate.plusMonths(dto.getDuration().getMonths());

        SyndicSubscription subscription = new SyndicSubscription();
        subscription.setSyndic(currentSyndic);
        // En self-service, le payeur est le syndic lui-même
        subscription.setInitiatedBy(currentSyndic);
        subscription.setSyndicPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentStatus(PaymentStatus.PENDING);
        subscription.setAmountPaid(amount);
        subscription.setMethod(dto.getMethod());
        subscription.setDuration(dto.getDuration());
        subscription.setTransactionRef(transactionRef);
        subscription.setStartDate(startDate);
        subscription.setEndDate(endDate);
        syndicSubscriptionRepository.save(subscription);

        // 6. Construit l'URL du pont de paiement TouchPay pour cette référence
        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        log.info("Changement de formule initié pour syndic {} — nouvelle formule {} — ref: {}",
                currentSyndic.getEmail(), plan.getName(), transactionRef);

        return SyndicPlanChangeResponseDTO.builder()
                .success(true)
                .message("Changement de formule initié. Veuillez compléter le paiement via TouchPay.")
                .transactionReference(transactionRef)
                .amount(amount)
                .paymentUrl(bridgeUrl)
                .build();
    }

    // ============================================================
    // Méthodes Automatiques
    // ============================================================

    /**
     * Fait passer en EXPIRED les abonnements ACTIVE dont la date de fin est dépassée.
     * S'exécute toutes les heures — même logique que pour les abonnements prestataires.
     */
    @Scheduled(cron = "0 0 * * * *") // toutes les heures, à la minute 0
    @Transactional
    public void expireOutdatedSyndicSubscriptions() {

        List<SyndicSubscription> expired = syndicSubscriptionRepository
                .findByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, LocalDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        for (SyndicSubscription subscription : expired) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
        }

        syndicSubscriptionRepository.saveAll(expired);

        // Notifie les admins pour chaque abonnement syndic qui vient d'expirer
        for (SyndicSubscription subscription : expired) {
            User syndic = subscription.getSyndic();
            String companyName = syndicProfileRepository.findByUserId(syndic.getId())
                    .map(SyndicProfile::getCompanyName)
                    .orElse(null);
            adminNotificationPreferenceService.notifyAdmins(
                    AdminNotificationEventType.SUBSCRIPTION_EXPIRED,
                    "Abonnement expiré",
                    "L'abonnement du syndic " +
                            (companyName != null ? companyName : syndic.getFirstName() + " " + syndic.getLastName()) +
                            " a expiré.");
        }

        log.info("{} abonnement(s) syndic passé(s) en EXPIRED", expired.size());
    }

    /**
     * Prévient le syndic 10 jours avant l'expiration de son abonnement — pour mensuel comme
     * annuel, sans distinction (60 jours n'aurait aucun sens sur un cycle mensuel de 30 jours).
     * Non filtré par une préférence syndic : c'est une info de facturation, pas optionnelle.
     */
     @Scheduled(cron = "0 0 8 * * *") // Exécute cette tâche automatiquement tous les jours à 8h
     @Transactional(readOnly = true)
     public void remindSyndicsOfUpcomingExpiration() {

       // Calcule la date située 10 jours après aujourd'hui
       LocalDate targetDate = LocalDate.now().plusDays(10);

       // Récupère les abonnements actifs qui expirent exactement dans 10 jours
        List<SyndicSubscription> expiringSoon = syndicSubscriptionRepository.findActiveExpiringBetween(
            targetDate.atStartOfDay(), // Inclusif : début de la journée
            targetDate.atTime(23, 59, 59)); // Exclusif : fin de la journée

        // Envoie une notification à chaque syndic concerné
        for (SyndicSubscription subscription : expiringSoon) {
            notificationService.sendPush(
                subscription.getSyndic().getId(),
                "Abonnement bientôt expiré",
                "Votre abonnement \"" + subscription.getSyndicPlan().getName() +
                        "\" expire le " + subscription.getEndDate().toLocalDate() +
                        ". Pensez à le renouveler.");
        }

       // Trace le nombre de rappels envoyés dans les logs
       if (!expiringSoon.isEmpty()) {
          log.info("{} rappel(s) d'expiration d'abonnement syndic envoyé(s)", expiringSoon.size());
        }
    }

    

    // ============================================================
    // Méthodes Utilitaires
    // ============================================================

    // Convertit le statut d'abonnement en label lisible pour l'affichage de la formule active
    // (ici EXPIRED décrit bien que la période actuelle n'est plus en cours)
    private String mapStatusToLabel(SubscriptionStatus status) {
        return switch (status) {
            case ACTIVE -> "Actif";
            case PENDING -> "En attente";
            case FAILED -> "Échoué";
            case EXPIRED -> "Expiré";
            case CANCELLED -> "Annulé";
            case DESACTIVATED -> "Désactivé";
        };
    }

    // Convertit le résultat du paiement en label pour l'historique — champ dédié, jamais retouché
    // après le callback, donc toujours fiable indépendamment de ce qui arrive à l'abonnement ensuite
    private String mapPaymentStatusToLabel(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case PENDING -> "En attente";
            case COMPLETED -> "Payé";
            case FAILED -> "Échoué";
        };
    }

    // Récupère le syndic actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    // Génère une référence courte et unique, préfixée selon le type de paiement (SYR-, SYN-, SUB-...)
    private String generateReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
