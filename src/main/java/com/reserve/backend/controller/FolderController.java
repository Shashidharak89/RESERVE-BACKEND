package com.reserve.backend.controller;

import com.reserve.backend.dto.ApiResponse;
import com.reserve.backend.dto.FolderMoveRequest;
import com.reserve.backend.dto.FolderRequest;
import com.reserve.backend.dto.FolderResponse;
import com.reserve.backend.security.UserPrincipal;
import com.reserve.backend.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(@Valid @RequestBody FolderRequest request,
                                                                    @AuthenticationPrincipal UserPrincipal currentUser) {
        FolderResponse response = folderService.createFolder(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Folder created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getFolders(@RequestParam(required = false) Long parentId,
                                                                         @RequestParam(required = false) String keyword,
                                                                         @RequestParam(required = false, defaultValue = "3") Integer order,
                                                                         @RequestParam(required = false, defaultValue = "1") Integer page,
                                                                         @RequestParam(required = false, defaultValue = "20") Integer limit,
                                                                         @AuthenticationPrincipal UserPrincipal currentUser) {
        List<FolderResponse> folders = folderService.getUserFolders(parentId, keyword, order, page, limit, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Folders retrieved successfully", folders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FolderResponse>> getFolderDetails(@PathVariable Long id,
                                                                        @AuthenticationPrincipal UserPrincipal currentUser) {
        FolderResponse response = folderService.getFolderDetails(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Folder details retrieved successfully", response));
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<ApiResponse<FolderResponse>> getPublicFolderDetails(@PathVariable Long id,
                                                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        FolderResponse response = folderService.getPublicFolderDetails(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Public folder details retrieved successfully", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FolderResponse>> renameFolder(@PathVariable Long id,
                                                                     @Valid @RequestBody FolderRequest request,
                                                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        FolderResponse response = folderService.renameFolder(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Folder renamed successfully", response));
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<ApiResponse<FolderResponse>> moveFolder(@PathVariable Long id,
                                                                  @RequestBody FolderMoveRequest request,
                                                                  @AuthenticationPrincipal UserPrincipal currentUser) {
        FolderResponse response = folderService.moveFolder(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Folder moved successfully", response));
    }

    @PostMapping("/copy/{id}")
    public ResponseEntity<ApiResponse<FolderResponse>> copyFolder(@PathVariable Long id,
                                                                   @RequestBody(required = false) FolderMoveRequest request,
                                                                   @AuthenticationPrincipal UserPrincipal currentUser) {
        FolderResponse response = folderService.copyFolderToPrivate(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Folder recursively copied to your private vault", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(@PathVariable Long id,
                                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        folderService.deleteFolder(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Folder deleted successfully"));
    }
}
