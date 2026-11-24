package com.example.solimus.enums;

/** Origine du paiement de travaux sur lequel la commission Solimus a été prélevée. */
public enum CommissionSource {
    /** Payé en ligne (TouchPay, Wave, Orange Money) : commission retenue avant crédit du wallet prestataire. */
    PAIEMENT_EN_LIGNE("Paiement en ligne"),
    /** Payé par le syndic depuis son wallet : commission retenue avant crédit du wallet prestataire. */
    WALLET_SYNDIC("Wallet du syndic"),
    /** Payé hors Solimus (preuve confirmée) : commission débitée du wallet prestataire. */
    HORS_PLATEFORME("Paiement hors plateforme"),
    /** Prélevée sur le wallet prestataire quand il termine les travaux (règle actuelle). */
    FIN_DE_TRAVAUX("Fin des travaux");

    private final String label;

    CommissionSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
