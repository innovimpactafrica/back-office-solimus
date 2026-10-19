package com.example.solimus.services.messaging;

import org.springframework.data.domain.Page;
import com.example.solimus.dtos.messaging.ConversationDTO;
import com.example.solimus.dtos.messaging.ConversationMessagesDTO;
import com.example.solimus.dtos.messaging.MessageDTO;
import com.example.solimus.dtos.messaging.SendMessageDTO;

import java.util.List;

/**
 * Messagerie interne 1:1 de la plateforme (MCO-03).
 * Accessible à tous les rôles authentifiés (syndic, copropriétaire, locataire, prestataire,
 * admin) — l'appartenance à la conversation est vérifiée à chaque appel côté service.
 */
public interface MessagingService {

    /** Liste paginée des conversations du courant connecté, triées par dernier message + compteur de non-lus. */
    Page<ConversationDTO> listConversations(int page, int size);

    /**
     * Ouvre (ou crée) la conversation 1:1 avec un autre utilisateur et retourne son aperçu.
     * Cloisonnement : uniquement syndic ↔ copropriétaire/locataire de SES résidences (403 sinon).
     * signalementId / interventionRequestId (facultatifs) rattachent le fil à un incident / des travaux.
     */
    ConversationDTO getOrCreateConversation(Long otherUserId, Long signalementId, Long interventionRequestId);

    /** Nombre total de messages non lus de l'utilisateur connecté, toutes conversations confondues. */
    long countUnreadMessages();

    /** Historique paginé d'une conversation (messages du plus récent au plus ancien). */
    ConversationMessagesDTO getMessages(Long conversationId, int page, int size);

    /** Envoie un message dans une conversation (idempotent si clientMessageId fourni). */
    MessageDTO sendMessage(Long conversationId, SendMessageDTO dto);

    /** Marque comme lus tous les messages REÇUS dans une conversation. */
    void markConversationAsRead(Long conversationId);
}
