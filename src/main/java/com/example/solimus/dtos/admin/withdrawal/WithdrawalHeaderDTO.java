package com.example.solimus.dtos.admin.withdrawal;

import com.example.solimus.enums.UserStatus;
import lombok.*;

// ===== DTO — Bloc 3, en-tête de la fiche détail d'une demande de retrait (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalHeaderDTO {

    private String photoUrl;
    private String companyName;
    private String responsibleName;
    private UserStatus accountStatus;
    // Uniquement pour un prestataire — null pour un syndic
    private String specialty;
    private String city;
}