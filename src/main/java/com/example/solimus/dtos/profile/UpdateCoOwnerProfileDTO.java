package com.example.solimus.dtos.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCoOwnerProfileDTO {

    private String firstName;

    private String lastName;

    private String phone;
}
