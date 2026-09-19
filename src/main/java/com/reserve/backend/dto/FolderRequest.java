package com.reserve.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FolderRequest {

    @NotBlank(message = "Folder name is required")
    private String name;

    private Long parentId;
}
