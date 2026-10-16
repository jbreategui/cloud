package com.cloud.app.service;

import com.cloud.app.model.ProcessingStatus;

import java.time.Instant;

/**
 * @param sensitive        la imagen se marco como contenido sensible al subirla
 * @param processingStatus estado de la copia desenfocada; null si la imagen no es sensible
 * @param blurredKey       key S3 de la copia desenfocada (sensitive/{uuid}.webp); null hasta que este DONE
 * @param blurredUrl       URL publica de la copia desenfocada; null hasta que este DONE
 */
public record ImageSummary(String key, String filename, String originalFilename, String contentType, long sizeBytes,
                            Instant uploadedAt, String url, long likes, boolean sensitive,
                            ProcessingStatus processingStatus, String blurredKey, String blurredUrl) {
}
