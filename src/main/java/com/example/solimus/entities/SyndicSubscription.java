package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_plan_id", nullable = false)
    private SyndicPlan syndicPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

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
