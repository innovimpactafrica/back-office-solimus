package com.example.solimus.dtos.admin.notification;

import com.example.solimus.enums.AdminNotificationEventType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

// ===== DTO — Une ligne du body de PUT /api/admin/notification-preferences =====
// Mise à jour partielle : platformEnabled/emailEnabled/smsEnabled sont optionnels
// (Boolean, pas boolean) — seuls les champs réellement envoyés (non null) sont modifiés
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAdminNotificationPreferenceDTO {

    @NotNull(message = "Le type d'événement est obligatoire")
    private AdminNotificationEventType eventType;

    private Boolean platformEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
}