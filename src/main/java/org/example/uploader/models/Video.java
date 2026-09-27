package org.example.uploader.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Document("videos")
public class Video {

    @Id
    private String id;
    private String originalFilename;
    private String objectKey;
    private String contentType;
    private long size;
    private long partSize;
    private int partCount;
    private String uploadId;
    private VideoStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String failureMessage;

    private Video() {
    }

    public static Video initiate(
            String originalFilename,
            String objectKey,
            String contentType,
            long size,
            long partSize,
            int partCount,
            String uploadId,
            Instant initiatedAt
    ) {
        requireNonBlank(originalFilename, "originalFilename");
        requireNonBlank(objectKey, "objectKey");
        requireNonBlank(contentType, "contentType");
        requireNonBlank(uploadId, "uploadId");
        if (size <= 0 || partSize <= 0 || partCount <= 0) {
            throw new IllegalArgumentException("Sizes and part count must be positive");
        }
        long expectedPartCount = (size - 1) / partSize + 1;
        if (partCount != expectedPartCount) {
            throw new IllegalArgumentException("Part count does not match size and part size");
        }
        Objects.requireNonNull(initiatedAt, "initiatedAt");

        Video video = new Video();
        video.id = UUID.randomUUID().toString();
        video.originalFilename = originalFilename;
        video.objectKey = objectKey;
        video.contentType = contentType;
        video.size = size;
        video.partSize = partSize;
        video.partCount = partCount;
        video.uploadId = uploadId;
        video.status = VideoStatus.INITIATED;
        video.createdAt = initiatedAt;
        video.updatedAt = initiatedAt;
        return video;
    }

    public void markReady(Instant readyAt) {
        requireValidTransitionTimestamp(readyAt);
        if (status == VideoStatus.READY) {
            return;
        }
        if (status == VideoStatus.ABORTED) {
            throw new IllegalStateException("An aborted video cannot become ready");
        }

        status = VideoStatus.READY;
        updatedAt = readyAt;
        completedAt = readyAt;
    }

    public void markAborted(String failureMessage, Instant abortedAt) {
        requireValidTransitionTimestamp(abortedAt);
        if (status == VideoStatus.ABORTED) {
            return;
        }
        if (status == VideoStatus.READY) {
            throw new IllegalStateException("A ready video cannot be aborted");
        }

        status = VideoStatus.ABORTED;
        this.failureMessage = failureMessage;
        updatedAt = abortedAt;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private void requireValidTransitionTimestamp(Instant timestamp) {
        Objects.requireNonNull(timestamp, "timestamp");
        if (timestamp.isBefore(createdAt)) {
            throw new IllegalArgumentException("Transition timestamp must not precede creation");
        }
    }

    public String getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public long getPartSize() {
        return partSize;
    }

    public int getPartCount() {
        return partCount;
    }

    public String getUploadId() {
        return uploadId;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureMessage() {
        return failureMessage;
    }
}
