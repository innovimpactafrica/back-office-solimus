package com.example.solimus.dtos.admin.withdrawal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

// ===== DTO — Bloc 5 : corps de la requête de refus d'une demande de retrait =====
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectWithdrawalRequestDTO {

    // Texte libre
    @NotBlank(message = "Le motif de refus est obligatoire")
    private String rejectionReason;

    @NotNull(message = "notifyUser est obligatoire")
    private Boolean notifyUser;
}