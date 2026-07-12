package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Table de relation entre un syndic et un copropriétaire.
 * Permet à un syndic d'ajouter un copropriétaire existant à sa liste.
 */
@Entity
@Table(name = "syndic_coowner_relation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyndicOwnerRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le syndic qui ajoute le copropriétaire à sa liste
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    // Le copropriétaire ajouté par le syndic
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "coowner_id", nullable = false)
    private User coOwner;

    // Date d'ajout du copropriétaire à la liste du syndic
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
