package com.example.solimus.entities;

import com.example.solimus.enums.ParticipantRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_participants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    // Null si participant externe (pas un user du système)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    // Uniquement ORGANISATEUR pour le syndic
    // Null pour tous les autres participants
    @Enumerated(EnumType.STRING)
    private ParticipantRole role;

    // Renseigné uniquement pour les externes (Mme Fall, M. Sow...)
    // Null si c'est un user du système
    private String externalName;

    // Rôle libre écrit par le syndic
    // Ex: "Présidente du conseil", "Trésorier", "Responsable technique"
    // Null si copropriétaire ordinaire sans rôle spécial
    private String roleLabel;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
