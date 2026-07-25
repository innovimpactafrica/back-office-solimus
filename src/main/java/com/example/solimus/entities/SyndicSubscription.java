package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// =============================================================================
// SYNDIC SUBSCRIPTION — Abonnement d'un syndic à une formule SyndicPlan
// Miroir de Subscription (prestataire), adapte au syndic.
// =============================================================================
@Entity
@Table(name = "syndic_subscriptions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyndicSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    // L'admin qui a initié la création du compte et paie l'abonnement (le syndic n'est pas
    // présent au moment du paiement) — c'est son contact qui doit être préaffiché dans TouchPay , contact admin
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "initiated_by_id")
    private User initiatedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_plan_id", nullable = false)
    private SyndicPlan syndicPlan;

    // État de vie de l'abonnement (ACTIVE/EXPIRED/CANCELLED/DESACTIVATED/...)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // Résultat du paiement de CETTE tentative précise — posé une seule fois au callback TouchPay
    // (COMPLETED ou FAILED) et plus jamais modifié ensuite, même si le statut de l'abonnement change
    // plus tard. Sert de source de vérité pour l'historique des paiements, indépendamment du cycle
    // de vie de l'abonnement.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Column(name = "transaction_ref", unique = true)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod method;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isCurrentlyActive() {
        return status == SubscriptionStatus.ACTIVE
                && endDate != null
                && endDate.isAfter(LocalDateTime.now());
    }
}
