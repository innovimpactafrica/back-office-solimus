package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.util.List;

// ===== DTO REPONSE - ONGLET HISTORIQUE D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingHistoryTabResponseDTO {

    private long totalCount;
    private List<MeetingHistoryRowDTO> history;
    private long currentPage;
    private long totalPages;
}