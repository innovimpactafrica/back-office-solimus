package com.example.solimus.dtos.admin.provider;

import com.example.solimus.enums.UserStatus;
import lombok.*;

// ===== DTO — Bloc A "En-tête" de la fiche détail prestataire (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderHeaderDTO {

    private String photoUrl;
    private String companyName;
    private UserStatus status;
    private String statusLabel;
    private String responsibleName;
    private String specialtyName;
}
