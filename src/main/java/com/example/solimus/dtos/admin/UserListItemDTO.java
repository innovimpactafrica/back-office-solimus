package com.example.solimus.dtos.admin;

import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserListItemDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private ERole role;
    private UserStatus status;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
