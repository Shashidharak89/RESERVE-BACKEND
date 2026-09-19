package com.reserve.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "public_uploads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename")
    private String storedFilename;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @NotBlank
    @Column(name = "cloudinary_url", nullable = false, length = 1024)
    private String cloudinaryUrl;

    @NotBlank
    @Column(name = "cloudinary_public_id", nullable = false)
    private String cloudinaryPublicId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
