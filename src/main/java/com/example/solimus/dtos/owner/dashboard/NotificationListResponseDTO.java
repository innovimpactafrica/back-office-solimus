package com.example.solimus.dtos.owner.dashboard;

import lombok.*;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationListResponseDTO {

    private long totalCount;
    private List<NotificationRowDTO> notifications;
    private long currentPage;
    private long totalPages;
}
