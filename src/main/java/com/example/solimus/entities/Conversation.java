package com.example.solimus.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Conversation de messagerie 1:1 entre deux utilisateurs de la plateforme
 * (syndic ↔ copropriétaire, copropriétaire ↔ prestataire, syndic ↔ prestataire, ...).
 *
 * Les deux participants sont ordonnés de façon canonique (participantA.id &lt; participantB.id)
 * afin qu'il n'existe qu'UNE seule conversation pour une paire d'utilisateurs donnée,
 * quel que soit le sens de la première prise de contact.
 */
@Entity
@Table(
        name = "conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversations_participants",
                columnNames = {"participant_a_id", "participant_b_id"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Participant le plus « petit » (id le plus faible) — côté canonique
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "participant_a_id", nullable = false)
    private User participantA;

    // Participant le plus « grand » (id le plus élevé) — côté canonique
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "participant_b_id", nullable = false)
    private User participantB;

    // Sujet / contexte facultatif de la conversation (ex: « Devis réparation fuite »)
    @Column(name = "subject")
    private String subject;

    // Contexte métier facultatif (MCO-03) : signalement à l'origine de l'échange, et demande de
    // travaux issue de sa conversion. Nullable — une conversation "libre" reste possible.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signalement_id")
    private Signalement signalement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_request_id")
    private InterventionRequest interventionRequest;

    // Date du dernier message — sert à trier la liste des conversations (plus récente en haut)
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Retourne l'interlocuteur (l'autre participant) par rapport à l'utilisateur passé. */
    public User otherParticipant(User user) {
        if (user == null) return null;
        if (participantA != null && participantA.getId() != null && participantA.getId().equals(user.getId())) {
            return participantB;
        }
        return participantA;
    }

    /** true si l'utilisateur donné est un des deux participants de cette conversation. */
    public boolean involves(User user) {
        if (user == null || user.getId() == null) return false;
        return (participantA != null && user.getId().equals(participantA.getId()))
                || (participantB != null && user.getId().equals(participantB.getId()));
    }
}
