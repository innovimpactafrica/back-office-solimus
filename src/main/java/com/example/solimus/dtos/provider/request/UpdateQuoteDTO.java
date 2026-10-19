package com.example.solimus.dtos.provider.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // un ancien "taxRate" est ignoré : TVA calculée côté serveur
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuoteDTO {

    private Long estimatedDelayId;
    private String additionalComments;
    private Boolean isDraft;
    @Valid
    private List<QuoteItemDTO> items;
}
