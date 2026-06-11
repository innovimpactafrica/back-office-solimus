package com.example.solimus.services.subscription;

import com.example.solimus.dtos.subscription.SouscrirePremiumDTO;
import com.example.solimus.dtos.subscription.SubscriptionDTO;
import com.example.solimus.dtos.subscription.SubscriptionPaymentDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.entities.Subscription;
import com.example.solimus.entities.SubscriptionPayment;
import com.example.solimus.entities.User;
import com.example.solimus.enums.SubscriptionPaymentStatus;
import com.example.solimus.enums.SubscriptionPlan;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.SubscriptionPaymentRepository;
import com.example.solimus.repositories.SubscriptionRepository;
import com.example.solimus.services.auth.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final EmailService emailService;

    @Value("${solimus.subscription.premium.price}")
    private BigDecimal premiumPrice;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    /**
     * Initialise un abonnement de niveau GRATUIT (par défaut) lors de l'inscription du prestataire.
     */
    @Override
    @Transactional
    public void initialiserAbonnement(User provider) {
        // Construction de l'abonnement par défaut
        Subscription subscription = Subscription.builder()
                .provider(provider)
                .plan(SubscriptionPlan.GRATUIT)
                .status(SubscriptionStatus.ACTIVE)
                .dateActivation(LocalDate.now())
                .dateExpiration(null) // gratuit = pas d'expiration
                .renouvellementAuto(false)
                .build();

        // Sauvegarde dans la base de données
        subscriptionRepository.save(subscription);
        log.info("Abonnement GRATUIT initialisé pour le prestataire : {}", provider.getEmail());
    }

    /**
     * Permet à un prestataire de passer au plan PREMIUM en simulant le paiement.
     */
    @Override
    @Transactional
    public PaymentResponseDTO passerEnPremium(Long providerId, SouscrirePremiumDTO dto) {
        Subscription subscription = subscriptionRepository
                .findByProviderId(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable pour ce prestataire"));

        if (subscription.getPlan() == SubscriptionPlan.PREMIUM
                && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            throw new BadRequestException("Vous avez déjà un abonnement Premium actif.");
        }

        String reference = genererReference("SUB");
        SubscriptionPayment paiement = SubscriptionPayment.builder()
                .reference(reference)
                .subscription(subscription)
                .montant(premiumPrice)
                .moyenPaiement(dto.getMoyenPaiement())
                .statut(SubscriptionPaymentStatus.EN_ATTENTE)
                .renouvellementAuto(dto.isRenouvellementAuto())
                .periode(LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + LocalDate.now().getYear())
                .build();

        subscriptionPaymentRepository.save(paiement);

        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, reference);

        return PaymentResponseDTO.builder()
                .success(true)
                .message("Paiement initié. Veuillez compléter via TouchPay.")
                .transactionReference(reference)
                .amountToPay(premiumPrice)
                .paymentUrl(bridgeUrl)
                .build();
    }

    /**
     * Tâche planifiée exécutée chaque jour à minuit pour renouveler les abonnements expirés.
     * Si le renouvellement automatique est activé, un nouveau paiement est simulé.
     * En cas d'échec du paiement, l'abonnement retourne au plan GRATUIT.
     */
    @Override
    @Scheduled(cron = "0 0 0 * * *") // chaque jour à minuit
    @Transactional
    public void renouvelerAbonnementsExpires() {
        LocalDate aujourdhui = LocalDate.now();

        // Trouver tous les abonnements Premium qui expirent aujourd'hui et dont le renouvellement auto est activé
        List<Subscription> aRenouveler = subscriptionRepository
                .findByPlanAndDateExpirationAndRenouvellementAutoTrue(
                        SubscriptionPlan.PREMIUM, aujourdhui);

        aRenouveler.forEach(sub -> {
            try {
                // Simuler le paiement automatique
                boolean paiementReussi = true; // simulé en V1

                if (paiementReussi) {
                    // Renouveler pour 1 mois supplémentaire
                    sub.setDateExpiration(sub.getDateExpiration().plusMonths(1));
                    subscriptionRepository.save(sub);

                    // Enregistrer la transaction de renouvellement
                    subscriptionPaymentRepository.save(
                            SubscriptionPayment.builder()
                                    .reference(genererReference())
                                    .subscription(sub)
                                    .montant(premiumPrice)
                                    .moyenPaiement(sub.getMoyenPaiement())
                                    .statut(SubscriptionPaymentStatus.PAYE)
                                    .periode(aujourdhui.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + aujourdhui.getYear())
                                    .build()
                    );

                    // Notifier par email de la réussite du renouvellement
                    if (sub.getProvider().isNotificationsEnabled()) {
                        emailService.sendSubscriptionRenewalNotification(
                                sub.getProvider().getEmail(),
                                sub.getProvider().getFirstName(),
                                sub.getPlan().name(),
                                sub.getDateExpiration().format(DATE_FORMATTER)
                        );
                    }
                    log.info("Abonnement Premium renouvelé avec succès pour le prestataire : {}", sub.getProvider().getEmail());
                } else {
                    // En cas d'échec de paiement → Rétrogradation automatique au plan GRATUIT
                    sub.setPlan(SubscriptionPlan.GRATUIT);
                    sub.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(sub);

                    // Notifier par email de l'échec et du passage en GRATUIT
                    if (sub.getProvider().isNotificationsEnabled()) {
                        emailService.sendSubscriptionRenewalFailedNotification(
                                sub.getProvider().getEmail(),
                                sub.getProvider().getFirstName()
                        );
                    }
                    log.warn("Échec du paiement de renouvellement. Rétrogradation du prestataire : {}", sub.getProvider().getEmail());
                }
            } catch (Exception e) {
                log.error("Erreur lors du renouvellement automatique pour le prestataire ID : {}", sub.getProvider().getId(), e);
            }
        });

        // Trouver tous les abonnements Premium qui expirent aujourd'hui et dont le renouvellement auto est désactivé
        List<Subscription> aRetrograder = subscriptionRepository
                .findByPlanAndDateExpirationAndRenouvellementAutoFalse(
                        SubscriptionPlan.PREMIUM, aujourdhui);

        aRetrograder.forEach(sub -> {
            try {
                sub.setPlan(SubscriptionPlan.GRATUIT);
                sub.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(sub);

                // Notifier par email de la fin de l'abonnement Premium
                if (sub.getProvider().isNotificationsEnabled()) {
                    emailService.sendSubscriptionExpiredNotification(
                            sub.getProvider().getEmail(),
                            sub.getProvider().getFirstName()
                    );
                }
                log.info("Abonnement Premium expiré. Rétrogradation automatique du prestataire : {}", sub.getProvider().getEmail());
            } catch (Exception e) {
                log.error("Erreur lors de la rétrogradation de l'abonnement expiré pour le prestataire ID : {}", sub.getProvider().getId(), e);
            }
        });
    }

    /**
     * Permet à un prestataire de consulter les détails de son abonnement actif et son historique de paiements.
     */
    @Override
    @Transactional(readOnly = true)
    public SubscriptionDTO getMonAbonnement(Long providerId) {
        Subscription subscription = subscriptionRepository
                .findByProviderId(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable pour ce prestataire"));

        return toDTO(subscription);
    }

    /**
     * Désactive le renouvellement automatique d'un abonnement.
     * Le statut passe à CANCELLED mais l'abonnement reste actif jusqu'à sa date d'expiration.
     */
    @Override
    @Transactional
    public void annulerAbonnement(Long providerId) {
        Subscription subscription = subscriptionRepository
                .findByProviderId(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        // Désactiver le renouvellement automatique
        subscription.setRenouvellementAuto(false);
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);

        // Envoyer la notification par email au prestataire
        if (subscription.getProvider().isNotificationsEnabled()) {
            emailService.sendSubscriptionCancellationNotification(
                    subscription.getProvider().getEmail(),
                    subscription.getProvider().getFirstName()
            );
        }
        log.info("Renouvellement automatique désactivé pour le prestataire ID : {}", providerId);
    }

    private String genererReference() {
        return "PAY-" + LocalDate.now().getYear() + "-" + String.format("%02d", LocalDate.now().getMonthValue()) + "-" + (int)(Math.random() * 900000 + 100000);
    }

    private String genererReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }

    private SubscriptionDTO toDTO(Subscription sub) {
        // Récupération de l'historique des paiements de cet abonnement
        List<SubscriptionPaymentDTO> historique = subscriptionPaymentRepository
                .findBySubscriptionIdOrderByCreatedAtDesc(sub.getId())
                .stream()
                .map(this::toPaymentDTO)
                .collect(Collectors.toList());

        // Mappage complet de l'entité Subscription vers SubscriptionDTO
        return SubscriptionDTO.builder()
                .plan(sub.getPlan().name())
                .status(sub.getStatus())
                .active(sub.getStatus() == SubscriptionStatus.ACTIVE)
                .dateActivation(sub.getDateActivation())
                .dateExpiration(sub.getDateExpiration())
                .moyenPaiement(sub.getMoyenPaiement())
                .renouvellementAuto(sub.isRenouvellementAuto())
                .avantages(sub.getPlan().getAvantages())
                .historiquePaiements(historique)
                .build();
    }

    private SubscriptionPaymentDTO toPaymentDTO(SubscriptionPayment p) {
        // Mappage d'un enregistrement d'historique de paiement vers son DTO
        return SubscriptionPaymentDTO.builder()
                .reference(p.getReference())
                .plan("Premium")
                .montant(p.getMontant())
                .date(p.getCreatedAt().toLocalDate())
                .moyenPaiement(p.getMoyenPaiement() != null ? p.getMoyenPaiement().name() : null)
                .statut(p.getStatut() != null ? p.getStatut().name() : null)
                .build();
    }
}
