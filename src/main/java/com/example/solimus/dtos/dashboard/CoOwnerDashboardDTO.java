package com.example.solimus.dtos.dashboard;

import com.example.solimus.dtos.meeting.MeetingSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDashboardDTO {

    private String firstName;
    private String lastName;
    private String photoUrl;
    private List<CoOwnerPropertyDTO> properties;
    private Long selectedPropertyId;
    private int totalDocuments;
    private List<ChargeAllocationSummaryDTO> chargesEnAttente;
    private List<MeetingSummaryDTO> prochainesReunions;
    
    // Santé financière de la résidence
    private BigDecimal soldeActuelResidence;
    private BigDecimal montantArrieresResidence;
}
