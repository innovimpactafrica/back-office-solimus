package com.example.solimus.services.payment;

/**
 * Service dédié à la gestion automatique des paiements via schedulers.
 * Gère l'expiration des paiements en attente (PENDING) qui ne sont pas
 * complétés dans le délai imparti.
 */
public interface PaymentSchedulerService {

    /**
     * Fait passer en FAILED les paiements PENDING créés depuis plus de 5 minutes
     * sans avoir reçu de callback TouchPay. S'exécute toutes les minutes.
     */
    void failStalePendingPayments();
}
