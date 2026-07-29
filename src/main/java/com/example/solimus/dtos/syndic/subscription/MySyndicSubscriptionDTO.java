package com.example.solimus.dtos.syndic.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// DTO — page "Mon abonnement" du syndic : formule active, dates, montant et fonctionnalités incluses
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MySyndicSubscriptionDTO {

    private String planName;
    private String description;

    // true si le statut est ACTIVE et que la date de fin n'est pas dépassée
    private boolean active;
    private String statusLabel;

    // Libellés affichables (ex: "Gestion des résidences") — pour l'affichage humain
    private List<String> features;

    // Codes bruts (ex: RESIDENCE_MANAGEMENT) — pour que le front compare fiablement sans dépendre du texte
    private List<String> featureCodes;

    // Limites de la formule — null signifie illimité pour ce critère précis
    private Integer maxResidences;
    private Integer maxApartments;

    private LocalDateTime activationDate;
    private LocalDateTime expirationDate;

    // Prix mensuel et annuel de la formule (référence affichée, indépendamment de la périodicité payée) —
    // le syndic a pu choisir l'un ou l'autre à la souscription, donc les deux sont utiles à l'affichage
    private BigDecimal monthlyAmount;
    private BigDecimal yearlyAmount;

    // Texte informatif uniquement pour l'instant — pas de renouvellement auto-débité réel
    private String nextRenewal;
}
