package com.example.solimus.dtos.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse de l'historique paginé d'une conversation :
 * la conversation (aperçu) + la page de messages.
 *
 * Les messages sont renvoyés du plus récent au plus ancien ; l'UI les inversera
 * pour afficher chronologiquement (le plus ancien en haut).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationMessagesDTO {

    private ConversationDTO conversation;
    private List<MessageDTO> messages;

    // Métadonnées de pagination
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
