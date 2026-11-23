package com.example.solimus.enums;

/** Mouvements du wallet de la plateforme Solimus (côté admin). */
public enum PlatformWalletTransactionCategory {
    COMMISSION_TRAVAUX("Commission sur travaux");

    private final String label;

    PlatformWalletTransactionCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
