package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.util.List;

// ===== DTO REPONSE - ONGLET DOCUMENTS D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDocumentsTabResponseDTO {

    private long totalCount;
    private List<MeetingDocumentRowDTO> documents;
    private long currentPage;
    private long totalPages;
}