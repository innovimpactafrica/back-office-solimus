package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// Entité représentant les contacts importants de la résidence.
// Un même numéro ne peut pas être utilisé 2 fois pour la même résidence (mais peut se répéter
// sur des résidences différentes) — contrôlé aussi côté service pour un message d'erreur clair.
@Entity
@Table(name = "residence_contacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"residence_id", "phone"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResidenceContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nom complet du contact clé de la résidence (optionnel)
    private String fullName;

    // Numéro de téléphone utilisé pour contacter cette personne (optionnel)
    private String phone;

    // Résidence à laquelle ce contact est rattaché
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    // Timestamps d'audit
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
