package com.example.solimus.enums;

public enum MeetingType {
    ORDINARY("Ordinaire"),
    EXTRAORDINARY("Extraordinaire");

    private final String label;

    MeetingType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}