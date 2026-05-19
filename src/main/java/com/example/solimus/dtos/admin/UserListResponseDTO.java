package com.example.solimus.dtos.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponseDTO {
    private List<UserListItemDTO> users;
    private long totalElements;
    private int totalPages;
    private int currentPage;
}
