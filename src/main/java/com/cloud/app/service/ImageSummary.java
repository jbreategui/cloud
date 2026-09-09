package com.cloud.app.service;

import java.time.Instant;

public record ImageSummary(String key, String originalFilename, String contentType, long sizeBytes,
                            Instant uploadedAt, String url) {
}
