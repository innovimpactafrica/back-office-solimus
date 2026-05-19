package com.example.solimus.dtos.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResidenceDTO {
    private Long id;
    private String name;
    private String fullAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer floorCount;
    private Integer apartmentCount;
    private Long syndicId;
    private String syndicName;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private java.time.LocalDateTime createdAt;
}
