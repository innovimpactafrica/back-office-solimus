package com.example.solimus.services.provider.subscription;

import com.example.solimus.dtos.admin.subscription.ProviderPlanDTO;
import com.example.solimus.dtos.provider.subscription.InitiateSubscriptionPaymentDTO;
import com.example.solimus.dtos.provider.subscription.SubscriptionPaymentResponseDTO;
import com.example.solimus.entities.ProviderPlan;
import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ProviderPlanRepository;
import com.example.solimus.repositories.SubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentServiceImpl implements SubscriptionPaymentService {

    private final ProviderPlanRepository providerPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    // ============================================================
    // Abonnement Prestataire
    // ============================================================
    @Override
    @Transactional
    public SubscriptionPaymentResponseDTO initiatePayment(InitiateSubscriptionPaymentDTO dto) {

        // On identifie le prestataire connecté grâce à son JWT
        User currentProvider = getCurrentUser();

        // On va chercher son abonnement le plus récent (s'il en a déjà eu un)
        subscriptionRepository.findFirstByProviderIdOrderByEndDateDesc(currentProvider.getId())

                // Si on en a trouvé un, on vérifie s'il est encore actif en ce moment précis
                .filter(ProviderSubscription::isCurrentlyActive)

                // S'il est toujours actif, on refuse la création d'un nouveau paiement
                .ifPresent(sub -> {

                    // Message clair pour que le prestataire comprenne pourquoi ça bloque
                    throw new BadRequestException(
                            "Vous avez déjà un abonnement actif jusqu'au " + sub.getEndDate());
                });

        // On récupère la formule actuellement configurée par l'admin (nom, prix...)
        ProviderPlan plan = providerPlanRepository.findFirstByOrderByIdAsc()

                // Si l'admin n'a jamais créé de formule, on ne peut pas continuer
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune formule d'abonnement n'est configurée."));

        // On regarde ce que le prestataire a choisi dans le DTO : annuel ou pas
        boolean isAnnual = Boolean.TRUE.equals(dto.getAnnual());

        // S'il a choisi annuel, on prend le prix annuel du plan ; sinon le prix mensuel
        BigDecimal amount = isAnnual ? plan.getYearlyPrice() : plan.getMonthlyPrice();

        // Si le prix correspondant n'a pas été configuré par l'admin (encore null en base)
        if (amount == null) {
            // On bloque avec un message qui précise lequel des deux prix manque
            throw new BadRequestException(
                    isAnnual ? "Le prix annuel n'est pas configuré." : "Le prix mensuel n'est pas configuré.");
        }

        // On génère une référence unique, préfixée "SUB-" pour que le bridge/callback la reconnaisse
        String transactionRef = generateReference("SUB");

        // On crée l'objet Subscription qui va représenter cette tentative de paiement
        ProviderSubscription subscription = new ProviderSubscription();

        // On rattache cette souscription au prestataire connecté
        subscription.setProvider(currentProvider);

        // On garde la trace de quelle formule était active au moment du paiement
        subscription.setProviderPlan(plan);

        // Statut initial : en attente, car TouchPay n'a pas encore confirmé le paiement
        subscription.setStatus(SubscriptionStatus.PENDING);

        // On fige le montant exact demandé maintenant, même si l'admin change le prix plus tard
        subscription.setAmountPaid(amount);

        // On garde la méthode choisie (Wave/Orange Money/Carte) pour l'historique admin
        subscription.setMethod(dto.getMethod());

        // On stocke la référence générée, utilisée pour retrouver cette ligne au callback
        subscription.setTransactionRef(transactionRef);

        // La date de début est fixée à maintenant, peu importe si le paiement réussit ou échoue
        subscription.setStartDate(LocalDateTime.now());

        // La date de fin dépend du choix annuel/mensuel : +1 an ou +1 mois à partir de maintenant
        subscription.setEndDate(isAnnual
                ? LocalDateTime.now().plusYears(1)
                : LocalDateTime.now().plusMonths(1));

        // On sauvegarde cette ligne PENDING en base — elle existe avant même la confirmation TouchPay
        subscriptionRepository.save(subscription);

        // On construit l'URL de la WebView TouchPay en y insérant la référence générée plus haut
        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        // On trace l'événement dans les logs pour debug/suivi
        log.info("Paiement abonnement initié pour prestataire {} — ref: {}",
                currentProvider.getId(), transactionRef);

        // On retourne au front tout ce qu'il faut pour ouvrir la WebView et afficher le montant
        return SubscriptionPaymentResponseDTO.builder()
                .success(true)
                .message("Paiement initié. Veuillez compléter via TouchPay.")
                .transactionReference(transactionRef)
                .amount(amount)
                .paymentUrl(bridgeUrl)
                .build();
    }

    /**
     * Retourne le plan d'abonnement actuel pour les prestataires.
     */
    @Override
    public ProviderPlanDTO getProviderPlan() {
        ProviderPlan plan = providerPlanRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new RuntimeException("Aucun plan d'abonnement configuré"));
        return toDTO(plan);
    }

    // ============================================================
    // Méthodes Automatiques
    // ============================================================

    /**
     * Fait passer en EXPIRED les abonnements ACTIVE dont la date de fin
     * est dépassée. S'exécute toutes les heures.
     */
    @Scheduled(cron = "0 0 * * * *") // toutes les heures, à la minute 0
    @Transactional
    public void expireOutdatedSubscriptions() {

        // On récupère toutes les Subscription encore marquées ACTIVE
        // mais dont la date de fin est déjà passée par rapport à maintenant
        List<ProviderSubscription> expired = subscriptionRepository
                .findByStatusAndEndDateBefore(SubscriptionStatus.ACTIVE, LocalDateTime.now());

        // S'il n'y en a aucune, pas besoin d'aller plus loin
        if (expired.isEmpty()) {
            return;
        }

        // Pour chaque abonnement trouvé, on bascule son statut à EXPIRED
        for (ProviderSubscription subscription : expired) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
        }

        // On sauvegarde tous les changements en une seule fois
        subscriptionRepository.saveAll(expired);

        log.info("{} abonnement(s) passé(s) en EXPIRED", expired.size());
    }

    /**
     * Fait passer en FAILED les paiements PENDING créés depuis plus de 5 minutes
     * sans avoir reçu de callback TouchPay. S'exécute toutes les minutes.
     */
    @Scheduled(cron = "0 * * * * *") // toutes les minutes
    @Transactional
    public void failStalePendingPayments() {

        // On calcule la limite de temps : tout ce qui a été créé avant cet instant
        // est considéré comme "trop vieux" pour rester PENDING
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(5);

        // On récupère les Subscription encore PENDING mais créées avant ce seuil
        List<ProviderSubscription> staleSubscriptions = subscriptionRepository
                .findByStatusAndCreatedAtBefore(SubscriptionStatus.PENDING, timeoutThreshold);

        if (staleSubscriptions.isEmpty()) {
            return;
        }

        // On les fait toutes basculer en FAILED — le prestataire devra réinitier un paiement
        for (ProviderSubscription subscription : staleSubscriptions) {
            subscription.setStatus(SubscriptionStatus.FAILED);
        }

        subscriptionRepository.saveAll(staleSubscriptions);

        log.info("{} paiement(s) abonnement passé(s) en FAILED après expiration du délai de 5 minutes",
                staleSubscriptions.size());
    }

    // ============================================================
    // Méthodes Utilitaires
    // ============================================================

    // Génère une référence courte et unique, préfixée selon le type de paiement (SUB-, PAY-, CPY-...)
    private String generateReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    // Convertit le statut d'abonnement en label lisible pour l'historique
    private String mapStatusToLabel(SubscriptionStatus status) {
        return switch (status) {
            case ACTIVE -> "Payé";
            case PENDING -> "En attente";
            case FAILED -> "Échoué";
            case EXPIRED -> "Expiré";
            case CANCELLED -> "Annulé";
        };
    }

    // Récupère l'utilisateur actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private ProviderPlanDTO toDTO(ProviderPlan plan) {
        return ProviderPlanDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .monthlyPrice(plan.getMonthlyPrice())
                .yearlyPrice(plan.getYearlyPrice())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
