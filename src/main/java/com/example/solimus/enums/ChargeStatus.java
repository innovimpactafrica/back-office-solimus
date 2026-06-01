package com.example.solimus.enums;

// Statuts de paiement d'une charge individuelle
public enum ChargeStatus {
    EN_ATTENTE,  // Non payée
    PAYEE,       // Payée
    EN_RETARD    // En retard (échéance dépassée)
}
