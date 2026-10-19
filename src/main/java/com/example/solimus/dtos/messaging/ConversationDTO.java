package com.example.solimus.dtos.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Aperçu d'une conversation dans la liste « Messagerie » du courant connecté :
 * l'interlocuteur, le dernier message échangé et le compteur de messages non lus.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationDTO {

    private Long id;
    private String subject;

    // Interlocuteur (l'autre participant de la conversation)
    private Long otherUserId;
    private String otherUserFirstName;
    private String otherUserLastName;
    private String otherUserRoleLabel;   // ex: « Syndic », « Prestataire »
    private String otherUserPhotoUrl;

    // Dernier message échangé (aperçu)
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;

    // Nombre de messages non lus dans cette conversation pour le courant connecté
    private long unreadCount;

    // Contexte métier facultatif (MCO-03) : signalement et demande de travaux issue de sa conversion
    private Long signalementId;
    private Long interventionRequestId;
}
