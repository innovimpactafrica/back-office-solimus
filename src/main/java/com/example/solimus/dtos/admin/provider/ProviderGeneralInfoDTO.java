package com.example.solimus.dtos.admin.provider;

import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Bloc C "Informations générales" de la fiche détail prestataire (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderGeneralInfoDTO {

    private String companyName;
    private String phone;
    private String address;
    private LocalDateTime registeredAt;
    private String email;
}
