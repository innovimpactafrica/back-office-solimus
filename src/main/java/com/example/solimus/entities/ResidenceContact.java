package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// Entité représentant les contacts importants de la résidence.
@Entity
@Table(name = "residence_contacts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResidenceContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nom complet du contact clé de la résidence
    @Column(nullable = false)
    private String fullName;

    // Fonction ou responsabilité du contact dans la résidence
    @Column(nullable = false)
    private String role;

    // Adresse email utilisée pour contacter cette personne
    private String email;

    // Numéro de téléphone utilisé pour contacter cette personne
    private String phone;

    // Photo ou avatar du contact
    @Column(name = "photo_url")
    private String photoUrl;

    // Résidence à laquelle ce contact est rattaché
    @ManyToOne(fetch = FetchType.LAZY)
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
