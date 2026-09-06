package com.cloud.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final S3Client s3Client;
    private final String bucket;

    public ImageStorageService(S3Client s3Client, @Value("${app.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Intento de subida con archivo vacio, nombre original={}", file.getOriginalFilename());
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            log.warn("Intento de subida con content-type no permitido: {}", contentType);
            throw new IllegalArgumentException("Tipo de archivo no permitido: " + contentType);
        }

        String key = "images/" + UUID.randomUUID() + extensionFor(contentType);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        log.info("Subiendo imagen a S3: bucket={}, key={}, size={} bytes, contentType={}",
                bucket, key, file.getSize(), contentType);

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            log.error("Error leyendo el archivo subido antes de enviarlo a S3, key={}", key, e);
            throw new UncheckedIOException("Error leyendo el archivo subido", e);
        } catch (SdkException e) {
            log.error("Error subiendo a S3, bucket={}, key={}", bucket, key, e);
            throw e;
        }

        log.info("Imagen subida con exito: bucket={}, key={}", bucket, key);
        return key;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
