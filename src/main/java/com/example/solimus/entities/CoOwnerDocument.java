package com.example.solimus.entities;

import com.example.solimus.enums.CoOwnerDocumentCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * ============================================================================
 * DOCUMENT D'UN COPROPRIÉTAIRE
 * ============================================================================
 * Document uploadé manuellement par le syndic (titre de propriété, contrat,
 * pièce d'identité). Distinct des PV d'assemblée (déjà gérés via MeetingDocument)
 * et des reçus de paiement (pas encore gérés — chantier séparé futur).
 */
@Entity
@Table(name = "co_owner_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "co_owner_id", nullable = false)
    private User coOwner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoOwnerDocumentCategory category;

    @Column(nullable = false)
    private String title; // ex: "Titre de propriété — Apt. 3B"

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileUrl;

    private Long fileSizeKb;

    private String fileType; // "PDF", "JPG", etc. — pour l'affichage

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
