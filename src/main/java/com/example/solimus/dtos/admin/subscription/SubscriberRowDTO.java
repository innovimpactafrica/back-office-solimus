package com.example.solimus.dtos.admin.subscription;

import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO LIGNE - LISTE DES ABONNES (Syndic + Prestataire fusionnés) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberRowDTO {

    private Long subscriptionId;
    private SubscriberType subscriberType;

    private String clientName;
    private String clientEmail;
    private String city;
    private String country;

    private String planName;
    private BigDecimal amount;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private SubscriptionStatus status;
    private String statusLabel;
}