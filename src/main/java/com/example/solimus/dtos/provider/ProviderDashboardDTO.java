package com.example.solimus.dtos.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO représentant les statistiques et indicateurs clés du tableau de bord prestataire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDashboardDTO {

    // Nom de l'entreprise du prestataire (ex: "Sen Plomberie")
    private String companyName;

    // Rôle de l'utilisateur (ex: "Prestataire")
    private String role;

    // URL de la photo de profil du prestataire
    private String profilePhotoUrl;

    // Nombre de notifications non lues
    private long unreadNotificationsCount;

    // ===== COMPTEURS PRINCIPAUX (KPIs) =====

    // Nombre total de demandes d'intervention reçues
    private int totalRequestsCount;

    // Nombre de devis envoyés en attente de validation par le syndic
    private int pendingQuotesCount;

    // Nombre d'interventions en cours de réalisation
    private int inProgressCount;

    // Nombre d'interventions validées et terminées
    private int validatedCount;
    // ===== VARIATIONS (%) PAR RAPPORT AU MOIS DERNIER =====

    // Tendance des demandes reçues (ex: +8.0%)
    private double requestsVariation;

    // Tendance des devis en attente (ex: -2.0%)
    private double pendingQuotesVariation;

    // Tendance des interventions en cours (ex: +1.0%)
    private double inProgressVariation;

    // Tendance des interventions validées (ex: +6.0%)
    private double validatedVariation;
    // ===== MISSIONS ET PAIEMENTS =====

    // Nombre de missions acceptées en attente de démarrage (ex: 3)
    private int pendingMissionsCount;

    // Montant total des paiements en attente (ex: 125000 FCFA)
    private BigDecimal pendingPaymentsAmount;

    // ===== PERFORMANCE HEBDOMADAIRE =====

    // Liste des gains financiers cumulés sur les 7 derniers jours (du Lundi au Dimanche glissant)
    private List<DailyRevenueDTO> performanceHebdo;

    // ===== STATS EN BAS =====
    
    // Revenu total cumulé disponible dans le portefeuille
    private BigDecimal totalRevenu;

    // Revenu moyen par jour sur les 7 derniers jours
    private BigDecimal moyenneParJour;

    // Nombre total d'interventions assignées à ce prestataire
    private int totalInterventions;

    // Variation des gains de la semaine courante par rapport à la semaine dernière (ex: +12%)
    private int variationHebdo;
}
