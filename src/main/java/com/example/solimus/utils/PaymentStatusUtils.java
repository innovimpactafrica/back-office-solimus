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
    // Public : réutilisé par le job planifié de relance (timeline Option C) pour ne pas
    // dupliquer ce seuil en dur (ex: "jour 31" = UNPAID_THRESHOLD_DAYS + 1)
    public static final long UNPAID_THRESHOLD_DAYS = 30;

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

        // Calcule le nombre de jours de retard, puis délègue au même seuil que ci-dessous
        long daysLate = ChronoUnit.DAYS.between(dueDate, today);
        return computeDelayStatusFromDaysLate(daysLate);
    }

    // Même règle que computeDelayStatus, mais à partir d'un nombre de jours de retard déjà connu
    // (ex: le plus grand retard parmi plusieurs charges d'un copropriétaire, déjà agrégé en base) —
    // évite de dupliquer le seuil de 30 jours partout où on ne part pas d'une seule échéance précise
    public static PaymentDelayStatus computeDelayStatusFromDaysLate(long daysLate) {
        if (daysLate <= 0) {
            return PaymentDelayStatus.UP_TO_DATE;
        }
        return daysLate <= UNPAID_THRESHOLD_DAYS ? PaymentDelayStatus.LATE : PaymentDelayStatus.UNPAID;
    }

    // Libellé String standard d'un statut de retard — seule source de vérité pour ce texte,
    // réutilisée par tous les DTO qui exposent un statut de paiement au Front (jamais "CRITIQUE")
    public static String toLabel(PaymentDelayStatus delayStatus) {
        return switch (delayStatus) {
            case UP_TO_DATE -> "A_JOUR";
            case LATE -> "RETARD";
            case UNPAID -> "IMPAYE";
        };
    }
}
