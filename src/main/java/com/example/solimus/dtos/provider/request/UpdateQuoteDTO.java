package com.example.solimus.dtos.provider.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuoteDTO {

    private Long estimatedDelayId;
    private String additionalComments;
    private Boolean isDraft;
    private List<QuoteItemDTO> items;
}
