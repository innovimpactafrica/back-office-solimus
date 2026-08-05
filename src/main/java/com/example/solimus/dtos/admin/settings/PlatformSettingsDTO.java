package com.example.solimus.dtos.admin.settings;

import lombok.*;

// ===== DTO — Réponse GET /api/admin/platform-settings =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettingsDTO {

    private String platformName;
    private String website;
    private String supportEmail;
    private String supportPhone;
}