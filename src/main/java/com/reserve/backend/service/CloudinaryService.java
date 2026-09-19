package com.reserve.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.reserve.backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public Map<String, Object> uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("Failed to upload empty file");
        }

        String originalFilename = file.getOriginalFilename();
        String mimeType = file.getContentType();
        String resourceType = determineResourceType(mimeType, originalFilename);

        String publicId = "reserve/" + UUID.randomUUID().toString();

        try {
            Map uploadParams = ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", resourceType,
                    "overwrite", true
            );

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            uploadResult.put("resource_type", resourceType);
            return uploadResult;
        } catch (IOException e) {
            log.error("Cloudinary upload failed for file {}: {}", originalFilename, e.getMessage());
            throw new BadRequestException("Could not upload file to Cloudinary: " + e.getMessage());
        }
    }

    public Map<String, Object> uploadFileBytes(byte[] bytes, String originalFilename, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Failed to upload empty file bytes");
        }

        String resourceType = determineResourceType(mimeType, originalFilename);
        String publicId = "reserve/" + UUID.randomUUID().toString();

        try {
            Map uploadParams = ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", resourceType,
                    "overwrite", true
            );

            Map uploadResult = cloudinary.uploader().upload(bytes, uploadParams);
            uploadResult.put("resource_type", resourceType);
            return uploadResult;
        } catch (IOException e) {
            log.error("Cloudinary upload failed for file {}: {}", originalFilename, e.getMessage());
            throw new BadRequestException("Could not upload file to Cloudinary: " + e.getMessage());
        }
    }

    public void deleteFile(String publicId, String resourceType) {
        try {
            Map deleteParams = ObjectUtils.asMap(
                    "resource_type", resourceType != null ? resourceType : "auto"
            );
            cloudinary.uploader().destroy(publicId, deleteParams);
        } catch (Exception e) {
            log.error("Failed to delete file {} from Cloudinary: {}", publicId, e.getMessage());
        }
    }

    public String determineResourceType(String mimeType, String filename) {
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                return "image";
            } else if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                return "video";
            }
        }

        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")) {
                return "image";
            } else if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") ||
                       lower.endsWith(".avi") || lower.endsWith(".mp3") || lower.endsWith(".wav")) {
                return "video";
            }
        }

        return "raw";
    }
}
