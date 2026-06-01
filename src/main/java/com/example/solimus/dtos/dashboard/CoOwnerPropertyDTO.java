package com.example.solimus.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerPropertyDTO {

    private Long id;
    private String reference;
    private String residenceName;
}
