package com.example.solimus.entities;

import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.PaymentType;
import com.example.solimus.enums.ProofPayerType;
import com.example.solimus.enums.ProofStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * PREUVE DE PAIEMENT HORS PLATEFORME D'UN ACOMPTE OU D'UN SOLDE DE TRAVAUX
 * ============================================================================
 * Le payeur (syndic ou copropriétaire, selon qui a accepté le devis) règle le prestataire en dehors
 * de Solimus et dépose un justificatif. Seul le PRESTATAIRE, qui reçoit les fonds, peut confirmer :
 * l'acompte est alors enregistré, ou le solde clôture l'intervention. Aucun wallet n'est mouvementé.
 * Une intervention a au plus UNE preuve en attente par type (acompte / solde).
 */
@Entity
@Table(name = "intervention_payment_proofs", indexes = {
        @Index(name = "idx_ipp_status", columnList = "status"),
        @Index(name = "idx_ipp_provider", columnList = "provider_id"),
        @Index(name = "idx_ipp_intervention", columnList = "intervention_request_id")
})
@Getter
@Setter
@NoArgsConstructor
public class InterventionPaymentProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence lisible, fixée après le 1er save à partir de l'id : PTV-000123
    @Column(unique = true, length = 30)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_request_id", nullable = false)
    private InterventionRequest interventionRequest;

    // ACOMPTE ou SOLDE
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, columnDefinition = "varchar(20)")
    private PaymentType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, columnDefinition = "varchar(20)")
    private ProofPayerType payerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    // Prestataire bénéficiaire (= prestataire sélectionné au dépôt) : seul habilité à valider
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", columnDefinition = "varchar(30)")
    private OffPlatformPaymentMethod paymentMethod;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "document_key", nullable = false, length = 500)
    private String documentKey;

    @Column(name = "document_name", length = 255)
    private String documentName;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private ProofStatus status = ProofStatus.PENDING_VALIDATION;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Référence du paiement enregistré à la validation (PAY-… acompte, SOL-… solde)
    @Column(name = "payment_reference", length = 30)
    private String paymentReference;
}
