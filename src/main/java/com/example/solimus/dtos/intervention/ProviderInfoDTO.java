package com.example.solimus.dtos.intervention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfoDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String companyName;
    private String interventionStatusLabel;
}
