package com.example.solimus.dtos.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerProfileDTO {

    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String photoUrl;

    @JsonFormat(pattern = "MMMM yyyy")
    private LocalDate memberSince;
}
