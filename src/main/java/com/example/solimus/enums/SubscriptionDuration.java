package com.example.solimus.enums;

// Durée de l'abonnement choisie à la création d'un syndic
public enum SubscriptionDuration {

    MONTHLY(1, "1 mois"),
    YEARLY(12, "12 mois");

    private final int months;
    private final String label;

    SubscriptionDuration(int months, String label) {
        this.months = months;
        this.label = label;
    }

    public int getMonths() {
        return months;
    }

    public String getLabel() {
        return label;
    }
}