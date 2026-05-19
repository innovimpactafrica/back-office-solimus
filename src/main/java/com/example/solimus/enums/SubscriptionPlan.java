package com.example.solimus.enums;

import java.util.List;

public enum SubscriptionPlan {
    GRATUIT {
        @Override
        public List<String> getAvantages() {
            return List.of(
                "3 devis par mois",
                "Accès basique aux demandes",
                "Support standard"
            );
        }
    },
    PREMIUM {
        @Override
        public List<String> getAvantages() {
            return List.of(
                "Devis illimités",
                "Priorité sur les interventions",
                "Support téléphonique prioritaire",
                "Statistiques avancées",
                "Badge 'Prestataire Premium'",
                "Visibilité augmentée de 50%"
            );
        }
    };

    public abstract List<String> getAvantages();
}
