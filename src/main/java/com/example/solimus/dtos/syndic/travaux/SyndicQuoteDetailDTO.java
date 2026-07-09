package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.dtos.provider.profile.QuoteLineDTO;
import com.example.solimus.enums.QuoteStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

//DTO du détail complet d'un devis (onglet 2 → clic sur un devis, syndic)
@Data
@Builder
public class SyndicQuoteDetailDTO {
    private Long id;
    private String reference;
    private String providerName;
    private LocalDateTime createdAt;
    private String estimatedDelayLabel;
    private String additionalComments;

    private List<QuoteLineDTO> mainOeuvre;
    private BigDecimal sousTotalMainOeuvre;
    private List<QuoteLineDTO> materiaux;
    private BigDecimal sousTotalMateriaux;
    private BigDecimal totalTTC;

    private QuoteStatus status;

    // Infos participants (copropriétaire, syndic, prestataire)
    private List<ParticipantDTO> participants;

    // Infos prestataire enrichies
    private String providerCompanyName;
    private String providerSpecialty;
    private Double providerRating;
    private String providerPhone;
    private String providerEmail;
}
