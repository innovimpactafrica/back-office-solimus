package com.example.solimus.dtos.admin.subscription;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

// ===== DTO — Suspension d'un compte abonné, saisie dans la modale "Suspendre le compte" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspendSubscriberDTO {

    @NotBlank(message = "Le motif de la suspension est obligatoire")
    private String reason;

    private boolean notifyClient;
}