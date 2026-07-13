package com.example.solimus.enums;

public enum MeetingStatus {
    DRAFT("Brouillon"),
    UPCOMING("À venir"),
    IN_PROGRESS("En cours"),
    COMPLETED("Terminée"),
    CANCELLED("Annulée");

    private final String label;

    MeetingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
