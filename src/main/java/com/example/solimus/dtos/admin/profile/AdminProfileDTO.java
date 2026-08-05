package com.example.solimus.dtos.admin.profile;

import lombok.*;

// ===== DTO — Réponse GET /api/admin/profile =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileDTO {

    private String photoUrl;
    private String fullName;
    private String role;
    private String email;
    private String phone;
}