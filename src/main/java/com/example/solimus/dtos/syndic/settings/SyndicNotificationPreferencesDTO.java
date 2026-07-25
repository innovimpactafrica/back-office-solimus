package com.example.solimus.dtos.syndic.settings;

import lombok.*;

// ===== DTO PRÉFÉRENCES DE NOTIFICATION SYNDIC =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicNotificationPreferencesDTO {

    private Boolean notifNewPayments;
    private Boolean notifUrgentIncidents;
    private Boolean notifUnpaidReminders;
    private Boolean notifAgReminders;
}