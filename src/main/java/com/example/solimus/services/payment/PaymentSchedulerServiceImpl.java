package com.example.solimus.services.payment;

import com.example.solimus.entities.ChargeCallPayment;
import com.example.solimus.entities.ExceptionalCallPayment;
import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.repositories.ChargeCallPaymentRepository;
import com.example.solimus.repositories.ExceptionalCallPaymentRepository;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSchedulerServiceImpl implements PaymentSchedulerService {

    private final PaymentRepository paymentRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final SyndicProfileRepository syndicProfileRepository;
    private final UserRepository userRepository;

    /**
     * Fait passer en FAILED les paiements PENDING créés depuis plus de 5 minutes
     * sans avoir reçu de callback TouchPay. S'exécute toutes les minutes.
     */
    @Override
    @Scheduled(cron = "0 * * * * *") // toutes les minutes
    @Transactional
    public void failStalePendingPayments() {

        // 1. Calculer la limite de temps : tout ce qui a été créé avant cet instant
        // est considéré comme "trop vieux" pour rester PENDING
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(5);

        // 2. Récupérer les paiements encore PENDING mais créés avant ce seuil
        List<PaymentProvider> stalePayments = paymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, timeoutThreshold);

        if (stalePayments.isEmpty()) {
            return;
        }

        // 3. Les faire toutes basculer en FAILED — l'initiateur devra réinitier un paiement
        for (PaymentProvider payment : stalePayments) {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.saveAll(stalePayments);

        log.info("{} paiements PENDING expirés et passés en FAILED", stalePayments.size());
    }

    /**
     * Fait passer en FAILED les paiements de charges PENDING créés depuis plus de 5 minutes
     * sans avoir reçu de callback TouchPay. S'exécute toutes les minutes.
     */
    @Scheduled(cron = "0 * * * * *") // toutes les minutes
    @Transactional
    public void failStalePendingChargePayments() {

        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(5);

        List<ChargeCallPayment> staleChargePayments = chargeCallPaymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, timeoutThreshold);

        if (staleChargePayments.isEmpty()) {
            return;
        }

        for (ChargeCallPayment payment : staleChargePayments) {
            payment.setStatus(PaymentStatus.FAILED);
        }

        chargeCallPaymentRepository.saveAll(staleChargePayments);

        log.info("{} paiements de charges PENDING expirés et passés en FAILED", staleChargePayments.size());
    }

    /**
     * Fait passer en FAILED les paiements de charges exceptionnelles PENDING créés depuis plus de 5 minutes
     * sans avoir reçu de callback TouchPay. S'exécute toutes les minutes.
     */
    @Scheduled(cron = "0 * * * * *") // toutes les minutes
    @Transactional
    public void failStalePendingExceptionalChargePayments() {

        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(5);

        List<ExceptionalCallPayment> staleExceptionalPayments = exceptionalCallPaymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, timeoutThreshold);

        if (staleExceptionalPayments.isEmpty()) {
            return;
        }

        for (ExceptionalCallPayment payment : staleExceptionalPayments) {
            payment.setStatus(PaymentStatus.FAILED);
        }

        exceptionalCallPaymentRepository.saveAll(staleExceptionalPayments);

        log.info("{} paiements de charges exceptionnelles PENDING expirés et passés en FAILED", staleExceptionalPayments.size());
    }

    /**
     * Fait passer en FAILED les abonnements syndics PENDING créés depuis plus de 5 minutes
     * sans avoir reçu de callback TouchPay. S'exécute toutes les minutes.
     */
    @Scheduled(cron = "0 * * * * *") // toutes les minutes
    @Transactional
    public void failStalePendingSyndicSubscriptions() {

        // 1. Calculer la limite de temps : tout ce qui a été créé avant cet instant est considéré comme "trop vieux" pour rester PENDING
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(5);

        // 2. Récupérer les abonnements syndics encore PENDING mais créés avant ce seuil
        List<SyndicSubscription> staleSubscriptions = syndicSubscriptionRepository
                .findByStatusAndCreatedAtBefore(SubscriptionStatus.PENDING, timeoutThreshold);

        // Rien à faire si aucun abonnement n'a expiré
        if (staleSubscriptions.isEmpty()) {
            return;
        }

        // 3. Les faire toutes basculer en FAILED — le compte syndic associé sera purgé juste après
        // par purgeOrphanedSyndicAccounts()
        for (SyndicSubscription subscription : staleSubscriptions) {
            // Statut de vie de l'abonnement
            subscription.setStatus(SubscriptionStatus.FAILED);
            // Résultat du paiement — posé ici une fois pour toutes, ne sera plus jamais modifié
            subscription.setPaymentStatus(PaymentStatus.FAILED);
        }

        syndicSubscriptionRepository.saveAll(staleSubscriptions);

        log.info("{} abonnement(s) syndic PENDING expiré(s) et passé(s) en FAILED", staleSubscriptions.size());
    }

    /**
     * Purge les comptes syndics orphelins : créés en même temps qu'un abonnement dont le paiement
     * n'a jamais abouti (FAILED, et le syndic n'a donc jamais été activé). On supprime l'abonnement,
     * le profil société puis le compte utilisateur — dans cet ordre pour respecter les contraintes de
     * clé étrangère — afin de libérer l'email/téléphone pour une nouvelle tentative de création.
     * S'exécute toutes les minutes, juste après le passage en FAILED des abonnements expirés.
     */
    @Scheduled(cron = "30 * * * * *") // toutes les minutes, décalé de 30s après l'expiration
    @Transactional
    public void purgeOrphanedSyndicAccounts() {

        // 1. Récupérer les abonnements FAILED dont le syndic n'a jamais été activé donc dont le paiement n'a jamais abouti, une seule fois, pour ce compte
        List<SyndicSubscription> orphaned = syndicSubscriptionRepository
                .findFailedWithNeverActivatedSyndic();

        // Rien à purger si aucun compte orphelin
        if (orphaned.isEmpty()) {
            return;
        }

        // 2. Supprimer chaque compte orphelin dans l'ordre imposé par les clés étrangères :
        // abonnement → profil → utilisateur
        for (SyndicSubscription subscription : orphaned) {
            // On garde l'id et l'email avant suppression, pour le log et pour retrouver le profil
            Long userId = subscription.getSyndic().getId();
            String email = subscription.getSyndic().getEmail();

            // Supprime d'abord l'abonnement (référence le user, doit partir en premier)
            syndicSubscriptionRepository.delete(subscription);
            // Supprime le profil société s'il existe
            syndicProfileRepository.findByUserId(userId).ifPresent(syndicProfileRepository::delete);
            // Supprime enfin le compte utilisateur — libère l'email/téléphone pour une nouvelle tentative
            userRepository.deleteById(userId);

            log.info("Compte syndic orphelin purgé (paiement jamais abouti) : {}", email);
        }
    }
}
