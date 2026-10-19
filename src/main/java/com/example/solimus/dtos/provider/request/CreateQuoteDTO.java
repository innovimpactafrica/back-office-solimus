package com.example.solimus.dtos.provider.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// DTO pour la création d'un devis par un prestataire (brouillon ou envoyé)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class CreateQuoteDTO {

    @NotNull(message = "L'ID de la demande est obligatoire")
    private Long interventionRequestId;

    @NotNull(message = "Le délai d'intervention est obligatoire")
    private Long estimatedDelayId;

    private String additionalComments;

    // Brouillon ou pas. @JsonProperty : sans lui, Lombok génère isDraft()/setDraft() et Jackson
    // n'expose que "draft" — un body {"isDraft": true} était ignoré et le devis partait en SENT.
    // "draft" reste accepté pour les clients qui l'envoyaient déjà.
    @JsonProperty("isDraft")
    @JsonAlias("draft")
    @Schema(description = "true = Brouillon, false = Envoi au client", example = "false")
    private boolean isDraft;

    // Pas de champ TVA : le taux (18 %) est appliqué côté serveur (VatUtil). Un "taxRate" envoyé
    // par un ancien client est ignoré (propriété inconnue), sans erreur.

    // Toutes les lignes (Matériel + Main d'œuvre)
    @Valid
    private List<QuoteItemDTO> items;
}
