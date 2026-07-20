package com.example.solimus.dtos.syndic.wallet;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResidenceOverviewDTO {

    private Long id;
    private String name;
    private String photoUrl;
    private Integer apartmentsCount;
    private Double collectionPercentage;
}
