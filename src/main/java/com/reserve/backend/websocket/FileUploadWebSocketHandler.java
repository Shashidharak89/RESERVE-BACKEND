package com.reserve.backend.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reserve.backend.dto.FileResponse;
import com.reserve.backend.security.CustomUserDetailsService;
import com.reserve.backend.security.JwtTokenProvider;
import com.reserve.backend.security.UserPrincipal;
import com.reserve.backend.service.FileService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileUploadWebSocketHandler extends TextWebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    private final Map<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();

    @Data
    @Builder
    private static class UploadSession {
        private String uploadId;
        private String filename;
        private long totalSize;
        private String mimeType;
        private boolean isShared;
        private Long folderId;
        private UserPrincipal user;
        private ByteArrayOutputStream buffer;
        private long bytesReceived;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode rootNode = objectMapper.readTree(message.getPayload());
            String type = rootNode.has("type") ? rootNode.get("type").asText() : "";

            switch (type) {
                case "INIT_UPLOAD":
                    handleInitUpload(session, rootNode);
                    break;
                case "UPLOAD_CHUNK":
                    handleUploadChunk(session, rootNode);
                    break;
                default:
                    log.warn("Unknown WebSocket message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket upload message", e);
            sendError(session, null, "Upload failed: " + e.getMessage());
        }
    }

    private void handleInitUpload(WebSocketSession session, JsonNode node) throws IOException {
        String uploadId = node.get("uploadId").asText();
        String filename = node.get("filename").asText();
        long totalSize = node.get("totalSize").asLong();
        String mimeType = node.has("mimeType") ? node.get("mimeType").asText() : "application/octet-stream";
        boolean isShared = node.has("isShared") && node.get("isShared").asBoolean();
        Long folderId = (node.has("folderId") && !node.get("folderId").isNull()) ? node.get("folderId").asLong() : null;
        String token = node.has("token") ? node.get("token").asText() : null;

        UserPrincipal userPrincipal = null;
        if (token != null && !token.isBlank() && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromJWT(token);
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            if (userDetails instanceof UserPrincipal) {
                userPrincipal = (UserPrincipal) userDetails;
            }
        }

        UploadSession uploadSession = UploadSession.builder()
                .uploadId(uploadId)
                .filename(filename)
                .totalSize(totalSize)
                .mimeType(mimeType)
                .isShared(isShared)
                .folderId(folderId)
                .user(userPrincipal)
                .buffer(new ByteArrayOutputStream())
                .bytesReceived(0)
                .build();

        uploadSessions.put(uploadId, uploadSession);
        log.info("Initialized WebSocket upload session {} for file {}", uploadId, filename);

        Map<String, Object> ack = Map.of(
                "type", "INIT_ACK",
                "uploadId", uploadId,
                "status", "READY"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
    }

    private void handleUploadChunk(WebSocketSession session, JsonNode node) throws IOException {
        String uploadId = node.get("uploadId").asText();
        UploadSession uploadSession = uploadSessions.get(uploadId);

        if (uploadSession == null) {
            sendError(session, uploadId, "Upload session not found or expired");
            return;
        }

        int chunkIndex = node.get("chunkIndex").asInt();
        int totalChunks = node.get("totalChunks").asInt();
        String base64Data = node.get("data").asText();

        byte[] chunkBytes = Base64.getDecoder().decode(base64Data);
        uploadSession.getBuffer().write(chunkBytes);
        uploadSession.setBytesReceived(uploadSession.getBytesReceived() + chunkBytes.length);

        long bytesReceived = uploadSession.getBytesReceived();
        long totalSize = uploadSession.getTotalSize();

        int percentage = (totalSize > 0) ? (int) Math.min(100, Math.round(((double) bytesReceived / totalSize) * 100)) : 100;

        Map<String, Object> progressMsg = Map.of(
                "type", "PROGRESS",
                "uploadId", uploadId,
                "chunkIndex", chunkIndex,
                "totalChunks", totalChunks,
                "bytesUploaded", bytesReceived,
                "totalBytes", totalSize,
                "percentage", percentage,
                "status", "UPLOADING"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(progressMsg)));

        if (chunkIndex == totalChunks - 1 || bytesReceived >= totalSize) {
            log.info("Final chunk received for uploadId {}. Total bytes: {}", uploadId, bytesReceived);
            byte[] completeFileBytes = uploadSession.getBuffer().toByteArray();

            try {
                FileResponse fileResponse = fileService.uploadWebSocketBytes(
                        completeFileBytes,
                        uploadSession.getFilename(),
                        uploadSession.getMimeType(),
                        uploadSession.getFolderId(),
                        uploadSession.isShared(),
                        uploadSession.getUser()
                );

                Map<String, Object> completeMsg = Map.of(
                        "type", "COMPLETE",
                        "uploadId", uploadId,
                        "percentage", 100,
                        "status", "COMPLETED",
                        "data", fileResponse
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(completeMsg)));
            } catch (Exception e) {
                log.error("Failed to complete Cloudinary/DB upload for session {}", uploadId, e);
                sendError(session, uploadId, "Cloudinary/DB upload failed: " + e.getMessage());
            } finally {
                uploadSessions.remove(uploadId);
            }
        }
    }

    private void sendError(WebSocketSession session, String uploadId, String message) {
        try {
            Map<String, Object> errorMsg = Map.of(
                    "type", "ERROR",
                    "uploadId", uploadId != null ? uploadId : "",
                    "message", message
            );
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
            }
        } catch (IOException e) {
            log.error("Failed to send WebSocket error message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
    }
}
