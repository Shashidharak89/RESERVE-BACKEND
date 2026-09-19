package com.reserve.backend.dto;

import lombok.Data;

@Data
public class FolderMoveRequest {
    private Long targetParentId;
}
