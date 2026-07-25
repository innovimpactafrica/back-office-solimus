package com.example.solimus.dtos.admin.syndic;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO RÉPONSE - NOUVEAU SYNDIC CRÉÉ (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSyndicResponseDTO {

    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String companyName;

    private String planName;
    private BigDecimal amountPaid;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private String transactionReference;
    private String paymentUrl;

    private String message;
}