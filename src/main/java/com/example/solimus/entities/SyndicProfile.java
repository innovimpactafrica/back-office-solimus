package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// =============================================================================
// PROFIL SYNDIC — Informations société d'un syndic
// =============================================================================
@Entity
@Table(name = "syndic_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyndicProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lien vers l'utilisateur
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    // Coordonnées GPS de l'adresse, remplies via l'autocomplete Google Places
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    // Préférences de notification — chacune activée par défaut
    @Column(name = "notif_new_payments", nullable = false)
    private Boolean notifNewPayments = true; // reçoit une notification à chaque paiement reçu

    @Column(name = "notif_urgent_incidents", nullable = false)
    private Boolean notifUrgentIncidents = true; // reçoit une alerte pour les incidents urgents

    @Column(name = "notif_unpaid_reminders", nullable = false)
    private Boolean notifUnpaidReminders = true; // reçoit une notification aux échéances de relance impayés

    @Column(name = "notif_ag_reminders", nullable = false)
    private Boolean notifAgReminders = true; // reçoit un rappel pour les AG planifiées

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}