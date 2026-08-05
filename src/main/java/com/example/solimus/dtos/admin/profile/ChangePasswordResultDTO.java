package com.example.solimus.dtos.admin.profile;

import lombok.*;

// ===== DTO — Réponse POST /api/admin/profile/change-password =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordResultDTO {

    private boolean success;
    private String message;
}