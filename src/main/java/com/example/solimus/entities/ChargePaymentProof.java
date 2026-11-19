package com.example.solimus.entities;

import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.OffPlatformPaymentMethod;
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
 * PREUVE DE PAIEMENT HORS PLATEFORME D'UNE CHARGE
 * ============================================================================
 * Le copropriétaire règle sa charge directement au syndic (virement, espèces, Wave…), puis dépose
 * ici un justificatif avec une description. La charge s'affiche « En attente de validation » ;
 * elle ne passe à « Payé » que lorsque le syndic valide la preuve. Rejetée, elle redevient à payer.
 * Une ligne de charge a au plus UNE preuve en attente à la fois ; l'historique est conservé.
 */
@Entity
@Table(name = "charge_payment_proofs", indexes = {
        @Index(name = "idx_cpp_status", columnList = "status"),
        @Index(name = "idx_cpp_residence", columnList = "residence_id"),
        @Index(name = "idx_cpp_coowner", columnList = "co_owner_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ChargePaymentProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence lisible, fixée après le 1er save à partir de l'id : PRV-000123
    @Column(unique = true, length = 30)
    private String reference;

    // REGULAR (ligne d'appel de charges) ou EXCEPTIONAL (ligne d'appel exceptionnel)
    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, columnDefinition = "varchar(20)")
    private ChargeType chargeType;

    // Renseigné si chargeType = REGULAR
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_call_item_id")
    private ChargeCallItem chargeCallItem;

    // Renseigné si chargeType = EXCEPTIONAL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exceptional_call_item_id")
    private ExceptionalCallItem exceptionalCallItem;

    // Copropriétaire qui a payé et déposé la preuve
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_owner_id", nullable = false)
    private User coOwner;

    // Résidence de la charge (dénormalisée : filtre et contrôle d'accès du syndic)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    // Montant restant dû au moment du dépôt (aucun paiement partiel : la preuve solde la charge)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", columnDefinition = "varchar(30)")
    private OffPlatformPaymentMethod paymentMethod;

    // Date du paiement déclarée par le copropriétaire (facultative)
    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(nullable = false, length = 1000)
    private String description;

    // Justificatif (clé MinIO), nom d'origine et type MIME
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

    // Syndic qui a validé ou rejeté
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Référence du paiement créé à la validation (CPY-… ou ECP-…), pour le reçu
    @Column(name = "payment_reference", length = 30)
    private String paymentReference;
}
