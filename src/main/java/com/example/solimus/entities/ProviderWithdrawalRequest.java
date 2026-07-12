package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// WithdrawalRequest.java — Demande de versement
@Entity
@Table(name = "withdrawal_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence unique
    @Column(unique = true, nullable = false)
    private String reference; // "WIT-2026-001"

    // Le prestataire qui demande le versement
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    // Montant demandé
    @Column(nullable = false)
    private BigDecimal amount;

    // Moyen de paiement choisi
    @Enumerated(EnumType.STRING)
    private PaymentMethod method; // WAVE, ORANGE_MONEY

    // Numéro de téléphone pour recevoir l'argent
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    // Statut de la demande
    @Enumerated(EnumType.STRING)
    private WithdrawalStatus status; // PENDING, COMPLETED, REJECTED

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt; // Date et heure de traitement

    @Column(name = "receipt_url")
    private String receiptUrl; // Reçu de paiement uploadé par l'admin

    @Column(name = "admin_comment")
    private String adminComment; // Commentaire ajouté par l'admin lors de la validation

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy; // Admin qui a traité la demande

    @Column(name = "motif_refus")
    private String motifRefus; //Motif de refus en cas de refus
}
