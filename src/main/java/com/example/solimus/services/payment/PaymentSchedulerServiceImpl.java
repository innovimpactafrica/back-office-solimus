package com.example.solimus.services.payment;

import com.example.solimus.entities.ChargeCallPayment;
import com.example.solimus.entities.ExceptionalCallPayment;
import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.repositories.ChargeCallPaymentRepository;
import com.example.solimus.repositories.ExceptionalCallPaymentRepository;
import com.example.solimus.repositories.PaymentRepository;
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
}
