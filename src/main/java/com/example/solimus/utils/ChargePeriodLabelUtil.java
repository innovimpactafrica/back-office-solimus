package com.example.solimus.utils;

import com.example.solimus.entities.ChargeCall;
import com.example.solimus.enums.ChargeFrequency;

/**
 * Libellé lisible de la période d'un appel de charges (« Août 2026 », « T3 2026 (Jul-Sep) ») —
 * seule source de vérité, partagée par « Mes charges » et les preuves de paiement.
 */
public final class ChargePeriodLabelUtil {

    private static final String[] MONTHS = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
    private static final String[] QUARTERS = {"Jan-Mar", "Avr-Jun", "Jul-Sep", "Oct-Dec"};

    private ChargePeriodLabelUtil() {
    }

    public static String periodLabel(ChargeCall chargeCall) {
        return periodLabel(chargeCall.getFrequency().name(), chargeCall.getPeriodNumber(), chargeCall.getYear());
    }

    public static String periodLabel(String frequency, int periodNumber, int year) {
        int index = periodNumber - 1;
        if (ChargeFrequency.MENSUEL.name().equals(frequency)) {
            String monthLabel = (index >= 0 && index < MONTHS.length) ? MONTHS[index] : "";
            return monthLabel + " " + year;
        }
        String quarterLabel = (index >= 0 && index < QUARTERS.length) ? QUARTERS[index] : "";
        return "T" + periodNumber + " " + year + " (" + quarterLabel + ")";
    }

    /** Titre d'une charge courante, ex. « Charges T3 2026 (Jul-Sep) ». */
    public static String chargeTitle(ChargeCall chargeCall) {
        return "Charges " + periodLabel(chargeCall);
    }
}
