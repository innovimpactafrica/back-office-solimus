package com.example.solimus.controllers;

import com.example.solimus.dtos.messaging.ConversationDTO;
import com.example.solimus.dtos.messaging.ConversationMessagesDTO;
import com.example.solimus.dtos.messaging.MessageDTO;
import com.example.solimus.dtos.messaging.SendMessageDTO;
import com.example.solimus.services.messaging.MessagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Messagerie interne 1:1 (MCO-03).
 *
 * Ces endpoints sont exposés sous /api/messages, qui n'est soumis à aucun préfixe de rôle
 * dans SecurityConfig → ils sont accessibles à TOUS les rôles authentifiés (syndic,
 * copropriétaire, locataire, prestataire, admin). L'appartenance à la conversation est
 * vérifiée dans le service : un utilisateur non participant reçoit un 403.
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Messagerie", description = "Messagerie interne 1:1 entre les utilisateurs de la plateforme")
public class MessageController {

    private final MessagingService messagingService;

    @Operation(summary = "Lister mes conversations", description = "Conversations de l'utilisateur connecté (paginées), triées par dernier message, avec compteur de non-lus")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Page de conversations") })
    @GetMapping
    public ResponseEntity<Page<ConversationDTO>> listConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(messagingService.listConversations(page, size));
    }

    @Operation(summary = "Nombre total de messages non lus", description = "Toutes conversations confondues, pour l'utilisateur connecté (badge)")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Compteur renvoyé") })
    @GetMapping("/unread-count")
    public ResponseEntity<Long> countUnreadMessages() {
        return ResponseEntity.ok(messagingService.countUnreadMessages());
    }

    @Operation(summary = "Ouvrir (ou créer) une conversation",
            description = "Ouvre la conversation 1:1 avec un autre utilisateur ; la crée si elle n'existe pas encore. "
                    + "Autorisé uniquement entre un syndic et les copropriétaires/locataires de ses résidences. "
                    + "signalementId / interventionRequestId (facultatifs) rattachent le fil à un incident / des travaux.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation trouvée ou créée"),
            @ApiResponse(responseCode = "400", description = "otherUserId manquant ou conversation avec soi-même",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Interlocuteur hors de vos résidences, ou signalement/travaux d'une autre résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur, signalement ou demande de travaux introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/conversations")
    public ResponseEntity<ConversationDTO> getOrCreateConversation(
            @RequestParam Long otherUserId,
            @RequestParam(required = false) Long signalementId,
            @RequestParam(required = false) Long interventionRequestId) {
        return ResponseEntity.ok(messagingService.getOrCreateConversation(otherUserId, signalementId, interventionRequestId));
    }

    @Operation(summary = "Historique paginé d'une conversation", description = "Messages du plus récent au plus ancien (l'UI les inverse pour l'affichage chronologique)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de messages"),
            @ApiResponse(responseCode = "403", description = "Vous ne faites pas partie de cette conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation introuvable")
    })
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ConversationMessagesDTO> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(messagingService.getMessages(conversationId, page, size));
    }

    @Operation(summary = "Envoyer un message", description = "Envoie un message dans la conversation (idempotent si clientMessageId fourni) et notifie l'interlocuteur")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message envoyé"),
            @ApiResponse(responseCode = "400", description = "Contenu vide"),
            @ApiResponse(responseCode = "403", description = "Vous ne faites pas partie de cette conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation introuvable")
    })
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageDTO> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messagingService.sendMessage(conversationId, dto));
    }

    @Operation(summary = "Marquer une conversation comme lue", description = "Marque comme lus tous les messages REÇUS dans cette conversation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation marquée comme lue"),
            @ApiResponse(responseCode = "403", description = "Vous ne faites pas partie de cette conversation"),
            @ApiResponse(responseCode = "404", description = "Conversation introuvable")
    })
    @PatchMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long conversationId) {
        messagingService.markConversationAsRead(conversationId);
        return ResponseEntity.ok().build();
    }
}
