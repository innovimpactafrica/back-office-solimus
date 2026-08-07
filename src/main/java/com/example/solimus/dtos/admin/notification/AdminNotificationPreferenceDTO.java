package com.example.solimus.dtos.admin.notification;

import com.example.solimus.enums.AdminNotificationEventType;
import lombok.*;

// ===== DTO — Une ligne de la matrice renvoyée par GET /api/admin/notification-preferences =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationPreferenceDTO {

    private AdminNotificationEventType eventType;
    private String label;
    private String description;
    private boolean platformEnabled;
    private boolean emailEnabled;
    private boolean smsEnabled;
}