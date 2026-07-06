package com.example.solimus.enums;

public enum MeetingDocumentType {
    CONVOCATION("Convocation"),
    FINANCIAL("Document financier"),
    REPORT("Rapport"),
    PV_AG("PV d'assemblée"),
    OTHER("Autre");

    private final String label;

    MeetingDocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
