package com.reserve.backend.controller;

import com.reserve.backend.dto.ApiResponse;
import com.reserve.backend.dto.FileMoveRequest;
import com.reserve.backend.dto.FileResponse;
import com.reserve.backend.dto.FileUpdateRequest;
import com.reserve.backend.security.UserPrincipal;
import com.reserve.backend.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileResponse>> uploadFile(@RequestParam("file") MultipartFile file,
                                                                 @RequestParam(value = "folderId", required = false) Long folderId,
                                                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        FileResponse response = fileService.uploadPrivateFile(file, folderId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileResponse>>> getFiles(@RequestParam(value = "folderId", required = false) Long folderId,
                                                                     @RequestParam(value = "search", required = false) String search,
                                                                     @RequestParam(value = "keyword", required = false) String keyword,
                                                                     @RequestParam(value = "order", required = false, defaultValue = "3") Integer order,
                                                                     @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                                     @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                                                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        List<FileResponse> files = fileService.getUserPrivateFiles(folderId, search, keyword, order, page, limit, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Files retrieved successfully", files));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileResponse>> getFileDetails(@PathVariable Long id,
                                                                     @AuthenticationPrincipal UserPrincipal currentUser) {
        FileResponse response = fileService.getPrivateFileDetails(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("File details retrieved successfully", response));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> downloadFile(@PathVariable Long id,
                                             @AuthenticationPrincipal UserPrincipal currentUser) {
        FileResponse response = fileService.getPrivateFileDetails(id, currentUser);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(response.getCloudinaryUrl()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + response.getOriginalFilename() + "\"")
                .build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FileResponse>> renameFile(@PathVariable Long id,
                                                                 @Valid @RequestBody FileUpdateRequest request,
                                                                 @AuthenticationPrincipal UserPrincipal currentUser) {
        FileResponse response = fileService.renameFile(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("File renamed successfully", response));
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<ApiResponse<FileResponse>> moveFile(@PathVariable Long id,
                                                               @RequestBody FileMoveRequest request,
                                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        FileResponse response = fileService.moveFile(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("File moved successfully", response));
    }

    @PostMapping("/copy/{id}")
    public ResponseEntity<ApiResponse<FileResponse>> copySharedFile(@PathVariable Long id,
                                                                    @RequestBody(required = false) FileMoveRequest request,
                                                                    @AuthenticationPrincipal UserPrincipal currentUser) {
        FileResponse response = fileService.copySharedFileToPrivate(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File copied to your private vault", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable Long id,
                                                         @AuthenticationPrincipal UserPrincipal currentUser) {
        fileService.deletePrivateFile(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("File deleted successfully"));
    }
}
