package com.example.solimus.dtos.tenant.profil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// DTO du profil du locataire connecté
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProfileDTO {

    private String firstName;

    private String lastName;

    private String email;

    private String phone;

    private String photoUrl;

    private String statusLabel; // "Locataire actif"

    private String residenceName;

    private String propertyReference;

    private LocalDateTime entryDate; // tenantAssignedAt
}
