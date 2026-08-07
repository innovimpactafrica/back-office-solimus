package com.example.solimus.utils;

import com.example.solimus.enums.PaymentDelayStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calcul centralisé du statut de retard de paiement — seule source de vérité du projet pour
 * cette règle, partagée entre tous les endroits qui affichent un statut de paiement.
 */
public class PaymentStatusUtils {

    // Seuil (en jours de retard) au-delà duquel un paiement passe de LATE à UNPAID
    private static final long UNPAID_THRESHOLD_DAYS = 30;

    // Calcule le statut de retard selon le nombre de jours écoulés depuis l'échéance
    // Règle : ≤ 30 jours de retard = LATE, > 30 jours = UNPAID
    public static PaymentDelayStatus computeDelayStatus(LocalDate dueDate, boolean isPaid, LocalDate today) {

        // Payé : toujours à jour, peu importe la date
        if (isPaid) {
            return PaymentDelayStatus.UP_TO_DATE;
        }

        // Pas encore échu : à jour
        if (dueDate == null || !dueDate.isBefore(today)) {
            return PaymentDelayStatus.UP_TO_DATE;
        }

        // Calcule le nombre de jours de retard
        long daysLate = ChronoUnit.DAYS.between(dueDate, today);

        return daysLate <= UNPAID_THRESHOLD_DAYS ? PaymentDelayStatus.LATE : PaymentDelayStatus.UNPAID;
    }
}
