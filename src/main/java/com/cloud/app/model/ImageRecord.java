package com.cloud.app.model;

import java.time.Instant;

public class ImageRecord {

    private Long id;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private Instant uploadedAt;
    private boolean sensitive;
    private ProcessingStatus processingStatus;
    private String blurredKey;

    public ImageRecord() {
    }

    public ImageRecord(String objectKey, String originalFilename, String contentType, long sizeBytes) {
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getBlurredKey() {
        return blurredKey;
    }

    public void setBlurredKey(String blurredKey) {
        this.blurredKey = blurredKey;
    }
}
