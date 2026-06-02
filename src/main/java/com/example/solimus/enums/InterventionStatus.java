package com.example.solimus.enums;

/**
 * Statuts possibles d'une demande d'intervention dans le workflow Solimus.
 */
public enum InterventionStatus {
    PENDING,            // Demande reçue / En attente
    SYNDIC_ASSIGNED,    // Pris en charge par le syndic
    QUOTE_SENT,         // Devis envoyé par un prestataire
    SYNDIC_VALIDATED,   // Devis validé par le syndic
    STARTED,            // Intervention démarrée
    FINISHED,           // Travail terminé
    FINAL_VALIDATION,   // Validation finale effectuée
    CANCELLED           // Demande annulée
}
