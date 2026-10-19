package com.example.solimus.dtos.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un message de la conversation. « mine » indique si l'auteur est le courant connecté,
 * ce qui permet à l'UI d'aligner à droite (ses propres messages) ou à gauche (les autres).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageDTO {

    private Long id;
    private Long conversationId;

    // Auteur du message
    private Long senderId;
    private String senderFirstName;
    private String senderLastName;
    private String senderPhotoUrl;

    private String content;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;   // null = non lu

    // true si ce message a été envoyé par le courant connecté
    private boolean mine;
}
