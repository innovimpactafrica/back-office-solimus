package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entité représentant une spécialité pour les prestataires.
 */
@Entity
@Table(name = "specialties")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Nom de l'icône pour l'affichage frontend (ex: "plumbing", "electrical")
     * Le frontend mappe ce nom vers une icône locale.
     */
    private String icon;
}
