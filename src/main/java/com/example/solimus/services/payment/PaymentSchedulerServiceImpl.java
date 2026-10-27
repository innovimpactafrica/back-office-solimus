package com.example.solimus.services.payment;

import com.example.solimus.entities.ChargeCallPayment;
import com.example.solimus.entities.ExceptionalCallPayment;
import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
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
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

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
     * Purge les comptes syndics orphelins : compte jamais activé (PENDING) dont tous les abonnements
     * sont FAILED (aucun paiement n'a jamais abouti). On supprime TOUS ses abonnements, le profil société
     * puis le compte utilisateur — dans cet ordre pour respecter les clés étrangères — afin de libérer
     * l'email/téléphone pour une nouvelle tentative de création.
     * Chaque compte est purgé dans sa propre transaction : un compte impossible à supprimer (autre
     * donnée liée) est journalisé et n'empêche pas la purge des suivants.
     * S'exécute toutes les minutes, juste après le passage en FAILED des abonnements expirés.
     */
    @Scheduled(cron = "30 * * * * *") // toutes les minutes, décalé de 30s après l'expiration
    public void purgeOrphanedSyndicAccounts() {

        // 1. Récupérer les syndics jamais activés dont aucun paiement n'a jamais abouti (une seule fois par compte)
        List<User> orphaned = syndicSubscriptionRepository.findNeverPaidPendingSyndics();

        // Rien à purger si aucun compte orphelin
        if (orphaned.isEmpty()) {
            return;
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 2. Supprimer chaque compte orphelin dans l'ordre imposé par les clés étrangères :
        // abonnements → profil → utilisateur
        for (User syndic : orphaned) {
            Long userId = syndic.getId();
            String email = syndic.getEmail();
            try {
                tx.executeWithoutResult(status -> {
                    // Tous les abonnements du syndic (il peut y avoir plusieurs tentatives échouées)
                    syndicSubscriptionRepository.deleteAll(syndicSubscriptionRepository.findBySyndicId(userId));
                    // Profil société s'il existe
                    syndicProfileRepository.findByUserId(userId).ifPresent(syndicProfileRepository::delete);
                    // Enfin le compte utilisateur — libère l'email/téléphone pour une nouvelle tentative
                    userRepository.deleteById(userId);
                });
                log.info("Compte syndic orphelin purgé (paiement jamais abouti) : {}", email);
            } catch (DataAccessException e) {
                log.warn("Purge impossible du compte syndic orphelin {} (id={}) : {}",
                        email, userId, e.getMostSpecificCause().getMessage());
            }
        }
    }
}
