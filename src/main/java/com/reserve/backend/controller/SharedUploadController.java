package com.reserve.backend.controller;

import com.reserve.backend.dto.ApiResponse;
import com.reserve.backend.dto.FileResponse;
import com.reserve.backend.security.UserPrincipal;
import com.reserve.backend.service.FileService;
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
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class SharedUploadController {

    private final FileService fileService;

    @PostMapping
    public ResponseEntity<ApiResponse<FileResponse>> uploadSharedFile(@RequestParam("file") MultipartFile file,
                                                                       @AuthenticationPrincipal UserPrincipal currentUser) {
        FileResponse response = fileService.uploadSharedFile(file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shared file uploaded successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileResponse>>> getSharedFiles(@RequestParam(value = "search", required = false) String search,
                                                                           @RequestParam(value = "keyword", required = false) String keyword,
                                                                           @RequestParam(value = "order", required = false, defaultValue = "3") Integer order,
                                                                           @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                                           @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        List<FileResponse> files = fileService.getSharedFiles(search, keyword, order, page, limit);
        return ResponseEntity.ok(ApiResponse.success("Shared files retrieved successfully", files));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileResponse>> getSharedFileDetails(@PathVariable Long id) {
        FileResponse response = fileService.getSharedFileDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Shared file details retrieved successfully", response));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> downloadSharedFile(@PathVariable Long id) {
        FileResponse response = fileService.getSharedFileDetails(id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(response.getCloudinaryUrl()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + response.getOriginalFilename() + "\"")
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSharedFile(@PathVariable Long id) {
        fileService.deleteSharedFile(id);
        return ResponseEntity.ok(ApiResponse.success("Shared file deleted successfully"));
    }
}
