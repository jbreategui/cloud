package com.cloud.app.service;

import java.time.Instant;

public record ImageSummary(String key, long sizeBytes, Instant lastModified, String url) {
}
