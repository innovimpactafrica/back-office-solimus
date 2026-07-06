package com.example.solimus.entities;

import com.example.solimus.enums.MeetingParticipantRole;
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

    /**
     * Le copropriétaire convoqué à cette AG.
     * Toujours renseigné — décision V1 : pas de participants externes,
     * uniquement les copropriétaires de la résidence.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    /**
     * Nom externe si le participant n'est pas un utilisateur enregistré
     */
    private String externalName;

    /**
     * Libellé du rôle pour affichage
     */
    private String roleLabel;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
