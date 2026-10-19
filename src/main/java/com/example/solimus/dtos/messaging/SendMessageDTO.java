package com.example.solimus.dtos.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps d'une requête d'envoi de message (POST /api/messages/{conversationId}/messages).
 *
 * clientMessageId est facultatif : fourni par le client, il rend l'envoi idempotent
 * (double-clic ou retry réseau → le message n'est enregistré qu'une seule fois).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SendMessageDTO {

    @NotBlank(message = "Le contenu du message ne peut pas être vide")
    @Size(max = 5000, message = "Le message ne peut pas dépasser 5000 caractères")
    private String content;

    private String clientMessageId;
}
