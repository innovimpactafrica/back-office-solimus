package com.example.solimus.dtos.admin.syndic;

import com.example.solimus.enums.UserStatus;
import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Bloc A "Informations Générales" de la fiche détail syndic (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicGeneralInfoDTO {

    private String firstName;
    private String lastName;
    private String companyName;
    private String email;
    private String phone;
    private String address;
    private String photoUrl;
    private LocalDateTime registeredAt;

    private UserStatus status;
    private String statusLabel;
}
