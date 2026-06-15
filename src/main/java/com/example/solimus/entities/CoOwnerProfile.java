package com.example.solimus.entities;

import com.example.solimus.enums.Nationality;
import com.example.solimus.enums.Title;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "co_owner_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Utilisateur associé (copropriétaire) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Civilité du copropriétaire */
    @Enumerated(EnumType.STRING)
    private Title title;

    /** Date de naissance */
    private LocalDate birthDate;

    /** Nationalité */
    @Enumerated(EnumType.STRING)
    private Nationality nationality;

    /** Numéro de téléphone secondaire */
    private String secondaryPhone;

    /** Adresse postale */
    @Column(columnDefinition = "TEXT")
    private String address;

    /** URL de la photo de profil */
    private String photoUrl;

    /** Date de création du profil */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Date de dernière mise à jour du profil */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
