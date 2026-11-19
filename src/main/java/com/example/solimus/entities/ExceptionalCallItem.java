package com.example.solimus.entities;

import com.example.solimus.enums.ProofStatus;
import com.example.solimus.enums.ChargeItemPaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "exceptional_call_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionalCallItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exceptional_call_id", nullable = false)
    private ExceptionalCall exceptionalCall;

    @Column(unique = true)
    private String reference; // Référence de l'appel de charges 

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "co_owner_id", nullable = false)
    private User coOwner;

    // Tantième snapshotté même en mode CUSTOM, pour référence/affichage
    private BigDecimal tantieme;

    // Calculée automatiquement (OWNERSHIP_SHARES) ou saisie manuellement (CUSTOM)
    @Column(nullable = false)
    private BigDecimal quotePart;

    @Column(nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    // Posé explicitement au moment de la confirmation d'un paiement (jamais recalculé à l'affichage)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeItemPaymentStatus status = ChargeItemPaymentStatus.PENDING;

    // Statut de la DERNIÈRE preuve de paiement hors plateforme (null = aucune preuve déposée).
    // N'influence aucun calcul financier : le statut ci-dessus ne passe à PAID qu'à la validation du syndic.
    // varchar (et non enum MySQL) : de nouvelles valeurs pourront être ajoutées sans migration.
    @Enumerated(EnumType.STRING)
    @Column(name = "proof_status", columnDefinition = "varchar(30)")
    private ProofStatus proofStatus;
}
