package com.example.solimus.scheduler;

import com.example.solimus.entities.ChargePayment;
import com.example.solimus.entities.Payment;
import com.example.solimus.entities.SubscriptionPayment;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionPaymentStatus;
import com.example.solimus.repositories.ChargePaymentRepository;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.SubscriptionPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpirationScheduler {

    private final PaymentRepository paymentRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final ChargePaymentRepository chargePaymentRepository;

    /**
     * Expiration des paiements PENDING depuis plus de 5 minutes.
     * Exécuté toutes les 2 minutes.
     */
    @Scheduled(fixedRate = 120000) // 2 minutes en ms
    @Transactional
    public void expirePendingPayments() {
        LocalDateTime expirationThreshold = LocalDateTime.now().minusMinutes(5);

        // 1. Paiements syndic (interventions)
        List<Payment> pendingPayments = paymentRepository.findByStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, expirationThreshold);
        for (Payment payment : pendingPayments) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.info("Paiement expiré (syndic) : {}", payment.getReference());
        }

        // 2. Paiements abonnements
        List<SubscriptionPayment> pendingSubscriptions = subscriptionPaymentRepository
                .findByStatutAndCreatedAtBefore(SubscriptionPaymentStatus.EN_ATTENTE, expirationThreshold);
        for (SubscriptionPayment subscription : pendingSubscriptions) {
            subscription.setStatut(SubscriptionPaymentStatus.ECHOUE);
            subscriptionPaymentRepository.save(subscription);
            log.info("Paiement expiré (abonnement) : {}", subscription.getReference());
        }

        // 3. Paiements charges copropriétaire
        List<ChargePayment> pendingCharges = chargePaymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, expirationThreshold);
        for (ChargePayment charge : pendingCharges) {
            charge.setStatus(PaymentStatus.FAILED);
            chargePaymentRepository.save(charge);
            log.info("Paiement expiré (charge) : {}", charge.getReference());
        }

        int totalExpired = pendingPayments.size() + pendingSubscriptions.size() + pendingCharges.size();
        if (totalExpired > 0) {
            log.info("Total paiements expirés ce cycle : {}", totalExpired);
        }
    }
}
