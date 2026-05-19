package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriptionPlan;
import com.example.solimus.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le prestataire abonné
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private User provider;

    // Plan actuel
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan; // GRATUIT, PREMIUM

    // Statut de l'abonnement
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status; // ACTIVE, EXPIRED, CANCELLED

    // Dates
    private LocalDate dateActivation;   // 01 Janvier 2026
    private LocalDate dateExpiration;   // 31 Décembre 2026

    // Renouvellement automatique
    private boolean renouvellementAuto;

    // Moyen de paiement utilisé
    @Enumerated(EnumType.STRING)
    private PaymentMethod moyenPaiement; // WAVE, ORANGE_MONEY

    @CreationTimestamp
    private LocalDateTime createdAt;
}
