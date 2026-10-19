package com.example.solimus.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Un message textuel au sein d'une {@link Conversation} 1:1.
 *
 * La date d'envoi (sentAt) est figée à la création. readAt est positionné par le destinataire
 * lorsque celui-ci ouvre la conversation (marquage en lot côté service).
 */
@Entity
@Table(
        name = "messages",
        indexes = @Index(name = "idx_messages_conversation_id", columnList = "conversation_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    // L'auteur du message
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // Contenu textuel du message
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Date de lecture par le destinataire — null tant que le message n'a pas été ouvert
    @Column(name = "read_at")
    private LocalDateTime readAt;

    // Identifiant généré côté client. Facultatif : s'il est fourni et que le même message
    // existe déjà pour cet expéditeur dans cette conversation, l'envoi est idempotent
    // (un double-clic / un retry réseau n'envoie pas le message une seconde fois).
    @Column(name = "client_message_id")
    private String clientMessageId;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;
}
