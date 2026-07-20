package com.example.solimus.dtos.owner.dashboard;

import lombok.*;
import java.time.LocalDateTime;

// ===== DTO LIGNE - NOTIFICATION =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRowDTO {

    private Long id;
    private String title;
    private String body;
    private boolean read;
    private LocalDateTime createdAt;
}
